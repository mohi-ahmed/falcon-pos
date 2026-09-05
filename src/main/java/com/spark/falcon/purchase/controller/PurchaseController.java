package com.spark.falcon.purchase.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.cashmanagement.dto.CashierShiftResponse;
import com.spark.falcon.cashmanagement.entity.CashLocationStatus;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import com.spark.falcon.cashmanagement.entity.RegisterStatus;
import com.spark.falcon.cashmanagement.exception.CashManagementAccessDeniedException;
import com.spark.falcon.cashmanagement.service.CashManagementService;
import com.spark.falcon.cashmanagement.exception.CashManagementValidationException;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.inventory.exception.InventoryAccessDeniedException;
import com.spark.falcon.inventory.exception.InventoryPostingException;
import com.spark.falcon.inventory.dto.ProductBatchResponse;
import com.spark.falcon.inventory.dto.BranchProductStockResponse;
import com.spark.falcon.inventory.service.InventoryQueryService;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.purchase.dto.command.ConfirmStockImportCommand;
import com.spark.falcon.purchase.dto.command.ReverseStockImportCommand;
import com.spark.falcon.purchase.dto.command.StockImportUploadCommand;
import com.spark.falcon.purchase.dto.request.CreatePurchaseRequest;
import com.spark.falcon.purchase.dto.request.ConfirmPurchaseRequest;
import com.spark.falcon.purchase.dto.request.PurchaseItemRequest;
import com.spark.falcon.purchase.dto.request.PurchaseReturnItemRequest;
import com.spark.falcon.purchase.dto.request.PurchaseReturnRequest;
import com.spark.falcon.purchase.dto.request.StockImportReverseRequest;
import com.spark.falcon.purchase.dto.request.StockImportUploadRequest;
import com.spark.falcon.purchase.dto.response.*;
import com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus;
import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus;
import com.spark.falcon.purchase.entity.enumtype.PurchaseStatus;
import com.spark.falcon.purchase.entity.enumtype.StockImportPurpose;
import com.spark.falcon.purchase.exception.PurchaseAccessDeniedException;
import com.spark.falcon.purchase.exception.PurchaseStateException;
import com.spark.falcon.purchase.exception.PurchaseValidationException;
import com.spark.falcon.purchase.exception.StockImportValidationException;
import com.spark.falcon.purchase.mapper.PurchaseMapper;
import com.spark.falcon.purchase.service.PurchaseReturnService;
import com.spark.falcon.purchase.service.PurchaseService;
import com.spark.falcon.purchase.service.StockImportService;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.dto.response.TaxRateResponse;
import com.spark.falcon.settings.dto.response.UnitResponse;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.service.PaymentMethodService;
import com.spark.falcon.settings.service.TaxRateAccessService;
import com.spark.falcon.settings.service.UnitAccessService;
import com.spark.falcon.supplier.dto.SupplierResponse;
import com.spark.falcon.supplier.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/owner/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 25, 50, 100);

    private final PurchaseService purchaseService;
    private final PurchaseReturnService purchaseReturnService;
    private final StockImportService stockImportService;
    private final PurchaseMapper purchaseMapper;
    private final BranchContextService branchContextService;
    private final BranchAccessService branchAccessService;
    private final SupplierService supplierService;
    private final ProductAccessService productAccessService;
    private final PaymentMethodService paymentMethodService;
    private final TaxRateAccessService taxRateAccessService;
    private final UnitAccessService unitAccessService;
    private final CashManagementService cashManagementService;
    private final InventoryQueryService inventoryQueryService;

    @GetMapping("/new")
    public String newPurchase(@RequestParam(required = false) Long supplierId,
                              @RequestParam(required = false) Long productId,
                              @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        CreatePurchaseRequest request = model.containsAttribute("purchaseRequest")
                ? (CreatePurchaseRequest) model.asMap().get("purchaseRequest")
                : newPurchaseRequest(setup,
                resolveInitialSupplierId(principal.ownerId(), setup, supplierId, productId), productId);
        populatePurchaseForm(model, principal.ownerId(), setup, request, null, productId);
        return "purchase/add-purchase";
    }

    @GetMapping("/{purchaseId}/edit")
    public String editDraft(@PathVariable Long purchaseId,
                            @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        PurchaseResponse purchase = purchaseService.findByOwnerAndId(principal.ownerId(), purchaseId);
        if (purchase.status() != PurchaseStatus.DRAFT) {
            throw new PurchaseStateException("Only Draft purchase may be edited");
        }
        CreatePurchaseRequest request = model.containsAttribute("purchaseRequest")
                ? (CreatePurchaseRequest) model.asMap().get("purchaseRequest") : purchaseRequest(purchase);
        populatePurchaseForm(model, principal.ownerId(), setup, request, purchaseId, null);
        return "purchase/add-purchase";
    }

    @GetMapping
    public String purchaseList(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) Long supplierId,
                               @RequestParam(required = false) PurchaseStatus status,
                               @RequestParam(required = false) PurchasePaymentStatus paymentStatus,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "purchaseDate") String sortBy,
                               @RequestParam(defaultValue = "desc") String sortDir,
                               @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        int safeSize = safeSize(size);
        String query = q == null ? "" : q.trim();
        String safeSortBy = Set.of("purchaseDate", "createdAt", "totalPayable", "paidAmount", "dueAmount", "paymentStatus").contains(sortBy)
                ? sortBy : "purchaseDate";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<PurchaseResponse> purchases = purchaseService.search(principal.ownerId(), setup.branchId(), supplierId,
                status, paymentStatus, fromDate, toDate, query,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(direction, safeSortBy).and(Sort.by(direction, "id"))));
        List<SupplierResponse> suppliers = activeSuppliersForBranch(principal.ownerId(), setup.branchId());
        model.addAttribute("setup", setup);
        model.addAttribute("purchases", purchases);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("supplierMap", supplierMap(activeSuppliers(principal.ownerId())));
        model.addAttribute("selectedSupplierId", supplierId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPaymentStatus", paymentStatus);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("query", query);
        model.addAttribute("selectedSize", safeSize);
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", direction.name().toLowerCase());
        model.addAttribute("activePage", "purchase-list");
        return "purchase/purchase-list";
    }

    @GetMapping("/{purchaseId}")
    public String purchaseDetails(@PathVariable Long purchaseId,
                                  @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        PurchaseDetailsResponse details = purchaseService.findDetails(principal.ownerId(), purchaseId);
        List<SupplierResponse> suppliers = activeSuppliers(principal.ownerId());
        model.addAttribute("setup", setup);
        model.addAttribute("details", details);
        model.addAttribute("purchase", details.purchase());
        model.addAttribute("supplierMap", supplierMap(suppliers));
        model.addAttribute("variantMap", variantMap(setup));
        model.addAttribute("batchMap", purchaseBatchMap(setup, details.purchase()));
        model.addAttribute("activePage", "purchase-list");
        return "purchase/purchase-details";
    }

    @GetMapping("/returns/new")
    public String newPurchaseReturn(@RequestParam Long purchaseId,
                                    @RequestParam(required = false) Long returnId,
                                    @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        PurchaseResponse purchase = purchaseService.findByOwnerAndId(principal.ownerId(), purchaseId);
        SupplierResponse supplier = supplierService.findByOwnerAndId(principal.ownerId(), purchase.supplierId()).orElse(null);

        PurchaseReturnResponse existingDraft = null;
        if (returnId != null) {
            existingDraft = purchaseReturnService.findByOwnerAndId(principal.ownerId(), returnId);
            if (existingDraft.status() != PurchaseReturnStatus.DRAFT) {
                throw new PurchaseStateException("Only Draft purchase return may be edited");
            }
            if (!existingDraft.purchaseId().equals(purchaseId)) {
                throw new PurchaseValidationException("Draft Purchase Return does not belong to the selected Purchase");
            }
        }

        PurchaseReturnRequest returnRequest = model.containsAttribute("purchaseReturnRequest")
                ? (PurchaseReturnRequest) model.asMap().get("purchaseReturnRequest")
                : existingDraft == null ? new PurchaseReturnRequest() : purchaseReturnRequest(existingDraft);

        if (returnRequest.getPurchaseId() == null) {
            returnRequest.setBranchId(setup.branchId());
            returnRequest.setPurchaseId(purchase.id());
            returnRequest.setReferenceNumber("PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            returnRequest.setReturnDate(LocalDate.now());
            returnRequest.setSettlementType(com.spark.falcon.purchase.entity.enumtype.PurchaseReturnSettlementType.REDUCE_SUPPLIER_DUE);
            returnRequest.setIdempotencyKey(UUID.randomUUID().toString());
        }

        model.addAttribute("setup", setup);
        model.addAttribute("purchaseReturnRequest", returnRequest);
        model.addAttribute("purchase", purchase);
        model.addAttribute("supplierName", supplier == null ? "Supplier #" + purchase.supplierId() : supplier.getName());
        model.addAttribute("variantMap", variantMap(setup));
        model.addAttribute("unitMap", unitMap(setup));
        model.addAttribute("paymentMethods", activePaymentMethods(principal.ownerId(), setup.branchId()));
        addCashPaymentContext(model, setup);
        model.addAttribute("returnIdempotencyKey", returnRequest.getIdempotencyKey());
        model.addAttribute("returnReference", returnRequest.getReferenceNumber());
        model.addAttribute("today", returnRequest.getReturnDate());
        model.addAttribute("editReturnId", returnId);
        model.addAttribute("returnItemRequestMap", returnRequest.getItems() == null ? Map.of() : returnRequest.getItems().stream()
                .filter(item -> item.getPurchaseItemId() != null)
                .collect(Collectors.toMap(PurchaseReturnItemRequest::getPurchaseItemId, Function.identity(), (a, b) -> a)));
        model.addAttribute("activePage", "purchase-returns");
        return "purchase/purchase-return-new";
    }

    @PostMapping("/returns")
    public String createPurchaseReturn(@Valid @ModelAttribute("purchaseReturnRequest") PurchaseReturnRequest request,
                                       BindingResult bindingResult,
                                       @RequestParam(defaultValue = "confirm") String submitAction,
                                       @RequestParam(required = false) Long returnId,
                                       @AuthenticationPrincipal OwnerPrincipal principal,
                                       RedirectAttributes redirectAttributes) {
        try {
            if ("confirmExisting".equalsIgnoreCase(submitAction)) {
                if (returnId == null) throw new PurchaseValidationException("Draft Purchase Return id is required");
                PurchaseReturnResponse result = purchaseReturnService.confirm(principal.ownerId(), returnId);
                redirectAttributes.addFlashAttribute("successMessage", "Purchase Return " + result.referenceNumber() + " confirmed.");
                return "redirect:/owner/purchases/returns/" + result.id();
            }
            if ("deleteDraft".equalsIgnoreCase(submitAction)) {
                if (returnId == null) throw new PurchaseValidationException("Draft Purchase Return id is required");
                purchaseReturnService.deleteDraft(principal.ownerId(), returnId);
                redirectAttributes.addFlashAttribute("successMessage", "Draft Purchase Return " + returnId + " deleted.");
                return "redirect:/owner/purchases/returns";
            }

            if (bindingResult.hasErrors()) {
                addValidationFlash(redirectAttributes, "purchaseReturnRequest", request, bindingResult);
                return purchaseReturnFailureRedirect(request.getPurchaseId(), returnId);
            }

            if ("saveDraft".equalsIgnoreCase(submitAction)) {
                PurchaseReturnResponse result = returnId == null
                        ? purchaseReturnService.createDraft(purchaseMapper.toReturnCommand(principal.ownerId(), request))
                        : purchaseReturnService.updateDraft(returnId, purchaseMapper.toReturnCommand(principal.ownerId(), request));
                redirectAttributes.addFlashAttribute("successMessage", "Draft Purchase Return " + result.referenceNumber() + " saved.");
                return "redirect:/owner/purchases/returns/new?purchaseId=" + result.purchaseId() + "&returnId=" + result.id();
            }

            PurchaseReturnResponse result;
            if (returnId == null) {
                result = purchaseReturnService.createAndConfirm(
                        purchaseMapper.toReturnCommand(principal.ownerId(), request));
            } else {
                purchaseReturnService.updateDraft(returnId, purchaseMapper.toReturnCommand(principal.ownerId(), request));
                result = purchaseReturnService.confirm(principal.ownerId(), returnId);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Purchase Return " + result.referenceNumber() + " confirmed.");
            return "redirect:/owner/purchases/returns/" + result.id();
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("purchaseReturnRequest", request);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return purchaseReturnFailureRedirect(request.getPurchaseId(), returnId);
        }
    }

    @GetMapping("/returns")
    public String purchaseReturnList(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) Long supplierId,
                                     @RequestParam(required = false) PurchaseReturnStatus status,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "returnDate") String sortBy,
                                     @RequestParam(defaultValue = "desc") String sortDir,
                                     @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        int safeSize = safeSize(size);
        String query = q == null ? "" : q.trim();
        String safeSortBy = Set.of("returnDate", "createdAt", "referenceNumber", "totalReturnAmount", "status").contains(sortBy)
                ? sortBy : "returnDate";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<PurchaseReturnResponse> returns = purchaseReturnService.search(principal.ownerId(), setup.branchId(), supplierId,
                status, fromDate, toDate, query,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(direction, safeSortBy).and(Sort.by(direction, "id"))));
        List<SupplierResponse> suppliers = activeSuppliersForBranch(principal.ownerId(), setup.branchId());
        model.addAttribute("setup", setup);
        model.addAttribute("returns", returns);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("supplierMap", supplierMap(activeSuppliers(principal.ownerId())));
        model.addAttribute("selectedSupplierId", supplierId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("query", query);
        model.addAttribute("selectedSize", safeSize);
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", direction.name().toLowerCase());
        model.addAttribute("activePage", "purchase-returns");
        return "purchase/purchase-return-list";
    }

    @GetMapping("/returns/{returnId}")
    public String purchaseReturnDetails(@PathVariable Long returnId,
                                        @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        PurchaseReturnDetailsResponse details = purchaseReturnService.findDetails(principal.ownerId(), returnId);
        List<SupplierResponse> suppliers = activeSuppliers(principal.ownerId());
        model.addAttribute("setup", setup(principal.ownerId()));
        model.addAttribute("details", details);
        model.addAttribute("purchaseReturn", details.purchaseReturn());
        model.addAttribute("supplierMap", supplierMap(suppliers));
        model.addAttribute("activePage", "purchase-returns");
        return "purchase/purchase-return-details";
    }

    @GetMapping("/logs")
    public String purchaseLog(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "25") int size,
                              @RequestParam(required = false) Long supplierId,
                              @RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "createdAt") String sortBy,
                              @RequestParam(defaultValue = "desc") String sortDir,
                              @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        int safeSize = safeSize(size);
        String safeSortBy = Set.of("createdAt", "action", "actorId").contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String query = q == null ? "" : q.trim();
        Page<PurchaseLogResponse> logs = purchaseService.findLogs(principal.ownerId(), setup.branchId(), supplierId, query,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(direction, safeSortBy).and(Sort.by(direction, "id"))));
        List<SupplierResponse> suppliers = activeSuppliers(principal.ownerId());
        model.addAttribute("setup", setup);
        model.addAttribute("logs", logs);
        model.addAttribute("supplierMap", supplierMap(suppliers));
        model.addAttribute("selectedSize", safeSize);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("selectedSupplierId", supplierId);
        model.addAttribute("query", query);
        model.addAttribute("sortBy", safeSortBy);
        model.addAttribute("sortDir", direction.name().toLowerCase());
        model.addAttribute("activePage", "purchase-log");
        return "purchase/purchase-log";
    }

    @GetMapping("/stock-import")
    public String stockImport(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        StockImportUploadRequest request = new StockImportUploadRequest();
        request.setBranchId(setup.branchId());
        request.setImportPurpose(StockImportPurpose.OPENING_STOCK);
        request.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("setup", setup);
        model.addAttribute("stockImportRequest", request);
        model.addAttribute("activePage", "stock-import");
        return "purchase/stock-import";
    }

    @GetMapping("/stock-import/template")
    public ResponseEntity<byte[]> stockImportTemplate(@RequestParam StockImportPurpose purpose,
                                                       @AuthenticationPrincipal OwnerPrincipal principal) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        StockImportTemplateResponse template = stockImportService.generateTemplate(principal.ownerId(), setup.branchId(), purpose);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(template.fileName(), StandardCharsets.UTF_8).build());
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        return ResponseEntity.ok().headers(headers).body(template.content());
    }

    @PostMapping("/stock-import")
    public String uploadStockImport(@Valid @ModelAttribute("stockImportRequest") StockImportUploadRequest request,
                                    BindingResult bindingResult,
                                    @AuthenticationPrincipal OwnerPrincipal principal,
                                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the Stock Import form errors.");
            redirectAttributes.addFlashAttribute("validationErrors", bindingResult.getFieldErrors().stream()
                    .map(this::validationMessage).distinct().toList());
            return "redirect:/owner/purchases/stock-import";
        }
        try {
            StockImportBatchResponse batch = stockImportService.uploadAndValidate(new StockImportUploadCommand(
                    principal.ownerId(), request.getBranchId(), request.getImportPurpose(), request.getFile().getOriginalFilename(),
                    request.getFile().getBytes(), request.getIdempotencyKey()));
            redirectAttributes.addFlashAttribute("successMessage", "Stock Import Batch " + batch.id() + " validated.");
            return "redirect:/owner/purchases/stock-import/history/" + batch.id();
        } catch (IOException | StockImportValidationException | PurchaseAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage() == null ? "Stock import could not be uploaded." : ex.getMessage());
            return "redirect:/owner/purchases/stock-import";
        }
    }

    @GetMapping("/stock-import/history")
    public String stockImportHistory(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) StockImportPurpose purpose,
                                     @RequestParam(required = false) com.spark.falcon.purchase.entity.enumtype.StockImportStatus status,
                                     @RequestParam(required = false) Long uploadedBy,
                                     @RequestParam(required = false) Long confirmedBy,
                                     @RequestParam(required = false) LocalDate uploadedFrom,
                                     @RequestParam(required = false) LocalDate uploadedTo,
                                     @RequestParam(required = false) LocalDate confirmedFrom,
                                     @RequestParam(required = false) LocalDate confirmedTo,
                                     @RequestParam(required = false) Boolean reversed,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "uploadedAt") String sortBy,
                                     @RequestParam(defaultValue = "desc") String sortDir,
                                     @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        int safeSize = safeSize(size);
        String query = q == null ? "" : q.trim();
        Long batchId = query.matches("\\d+") ? Long.valueOf(query) : null;
        String safeSortBy = Set.of("uploadedAt", "confirmedAt", "status", "importPurpose", "fileName", "id").contains(sortBy) ? sortBy : "uploadedAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        model.addAttribute("setup", setup);
        java.time.ZoneId branchZone = java.time.ZoneId.of(branchAccessService.findByBusinessIdAndBranchId(setup.businessId(), setup.branchId())
                .orElseThrow(PurchaseAccessDeniedException::new).timeZone());
        model.addAttribute("imports", stockImportService.searchHistory(principal.ownerId(), setup.branchId(), purpose, status,
                uploadedBy, confirmedBy, uploadedFrom == null ? null : uploadedFrom.atStartOfDay(branchZone).toInstant(),
                uploadedTo == null ? null : uploadedTo.plusDays(1).atStartOfDay(branchZone).toInstant(),
                confirmedFrom == null ? null : confirmedFrom.atStartOfDay(branchZone).toInstant(),
                confirmedTo == null ? null : confirmedTo.plusDays(1).atStartOfDay(branchZone).toInstant(),
                reversed, batchId, query, PageRequest.of(Math.max(0, page), safeSize, Sort.by(direction, safeSortBy).and(Sort.by(direction, "id")))));
        model.addAttribute("selectedSize", safeSize);
        model.addAttribute("purpose", purpose); model.addAttribute("status", status);
        model.addAttribute("uploadedBy", uploadedBy); model.addAttribute("confirmedBy", confirmedBy);
        model.addAttribute("uploadedFrom", uploadedFrom); model.addAttribute("uploadedTo", uploadedTo);
        model.addAttribute("confirmedFrom", confirmedFrom); model.addAttribute("confirmedTo", confirmedTo);
        model.addAttribute("reversed", reversed); model.addAttribute("query", query);
        model.addAttribute("sortBy", safeSortBy); model.addAttribute("sortDir", direction.name().toLowerCase());
        model.addAttribute("activePage", "stock-import-history");
        return "purchase/stock-import-history";
    }

    @GetMapping("/stock-import/history/{batchId}")
    public String stockImportDetails(@PathVariable Long batchId,
                                     @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        model.addAttribute("setup", setup(principal.ownerId()));
        model.addAttribute("stockImport", stockImportService.findByOwnerAndId(principal.ownerId(), batchId));
        model.addAttribute("reverseRequest", new StockImportReverseRequest());
        model.addAttribute("activePage", "stock-import-history");
        return "purchase/stock-import-details";
    }

    @PostMapping("/stock-import/{batchId}/confirm")
    public String confirmStockImport(@PathVariable Long batchId, @AuthenticationPrincipal OwnerPrincipal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            stockImportService.confirm(new ConfirmStockImportCommand(principal.ownerId(), batchId));
            redirectAttributes.addFlashAttribute("successMessage", "Stock Import Batch " + batchId + " posted.");
        } catch (StockImportValidationException | PurchaseAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/purchases/stock-import/history/" + batchId;
    }

    @PostMapping("/stock-import/{batchId}/reverse")
    public String reverseStockImport(@PathVariable Long batchId,
                                     @Valid @ModelAttribute("reverseRequest") StockImportReverseRequest request,
                                     BindingResult bindingResult,
                                     @AuthenticationPrincipal OwnerPrincipal principal,
                                     RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addValidationFlash(redirectAttributes, "reverseRequest", request, bindingResult);
            return "redirect:/owner/purchases/stock-import/history/" + batchId;
        }
        try {
            stockImportService.reverse(new ReverseStockImportCommand(principal.ownerId(), batchId, request.getReason()));
            redirectAttributes.addFlashAttribute("successMessage", "Stock Import reversal posted.");
        } catch (StockImportValidationException | PurchaseAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/purchases/stock-import/history/" + batchId;
    }

    @PostMapping
    public String createAndConfirm(@Valid @ModelAttribute("purchaseRequest") CreatePurchaseRequest request,
                                   BindingResult bindingResult,
                                   @RequestParam(required = false) String redirectTo,
                                   @RequestParam(defaultValue = "confirm") String submitAction,
                                   @AuthenticationPrincipal OwnerPrincipal principal,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addValidationFlash(redirectAttributes, "purchaseRequest", request, bindingResult);
            return purchaseFailureRedirect(request.getSupplierId(), redirectTo);
        }
        try {
            if ("draft".equalsIgnoreCase(submitAction)) {
                PurchaseResponse draft = purchaseService.createDraft(
                        purchaseMapper.toCreateCommand(principal.ownerId(), request));
                redirectAttributes.addFlashAttribute("successMessage", "Draft Purchase " + draft.id() + " saved.");
                return "redirect:/owner/purchases/" + draft.id() + "/edit";
            }

            PurchaseResponse purchase = purchaseService.createAndConfirm(purchaseMapper.toCreateCommand(principal.ownerId(), request));
            redirectAttributes.addFlashAttribute("successMessage", "Purchase " + purchase.id() + " confirmed successfully.");
            return "purchases".equalsIgnoreCase(redirectTo)
                    ? "redirect:/owner/purchases/" + purchase.id()
                    : supplierPurchase(purchase.supplierId());
        } catch (PurchaseValidationException | PurchaseStateException | PurchaseAccessDeniedException
                 | InventoryPostingException | InventoryAccessDeniedException
                 | CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("purchaseRequest", request);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return purchaseFailureRedirect(request.getSupplierId(), redirectTo);
        }
    }

    @PostMapping("/{purchaseId}")
    public String updateDraft(@PathVariable Long purchaseId,
                              @Valid @ModelAttribute("purchaseRequest") CreatePurchaseRequest request,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addValidationFlash(redirectAttributes, "purchaseRequest", request, bindingResult);
            return "redirect:/owner/purchases/" + purchaseId + "/edit";
        }
        try {
            purchaseService.updateDraft(purchaseId, purchaseMapper.toCreateCommand(principal.ownerId(), request));
            redirectAttributes.addFlashAttribute("successMessage", "Draft Purchase " + purchaseId + " updated.");
            return "redirect:/owner/purchases/" + purchaseId;
        } catch (PurchaseValidationException | PurchaseStateException | PurchaseAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("purchaseRequest", request);
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/owner/purchases/" + purchaseId + "/edit";
        }
    }

    @PostMapping("/{purchaseId}/delete")
    public String deleteDraft(@PathVariable Long purchaseId,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        try {
            purchaseService.deleteDraft(principal.ownerId(), purchaseId);
            redirectAttributes.addFlashAttribute("successMessage", "Draft Purchase " + purchaseId + " deleted.");
        } catch (PurchaseStateException | PurchaseAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/purchases";
    }

    @PostMapping("/{purchaseId}/confirm")
    public String confirmDraft(@PathVariable Long purchaseId,
                               @Valid @ModelAttribute ConfirmPurchaseRequest request,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstValidationMessage(bindingResult));
            return "redirect:/owner/purchases/" + purchaseId;
        }
        try {
            PurchaseResponse purchase = purchaseService.confirmDraft(
                    purchaseMapper.toConfirmCommand(principal.ownerId(), purchaseId, request));
            redirectAttributes.addFlashAttribute("successMessage", "Purchase " + purchase.id() + " confirmed.");
        } catch (PurchaseValidationException | PurchaseStateException | PurchaseAccessDeniedException
                 | InventoryPostingException | InventoryAccessDeniedException
                 | CashManagementValidationException | CashManagementAccessDeniedException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/owner/purchases/" + purchaseId;
    }

    private BusinessSetupResponse setup(Long ownerId) {
        BusinessSetupResponse setup = branchContextService.resolveOwnerSetup(ownerId);
        if (setup.businessId() == null || setup.branchId() == null) throw new PurchaseAccessDeniedException();
        return setup;
    }

    private Long resolveInitialSupplierId(Long ownerId, BusinessSetupResponse setup,
                                          Long requestedSupplierId, Long productId) {
        Set<Long> activeSupplierIds = activeSuppliersForBranch(ownerId, setup.branchId()).stream()
                .map(SupplierResponse::getId)
                .collect(Collectors.toSet());

        if (requestedSupplierId != null && activeSupplierIds.contains(requestedSupplierId)) {
            return requestedSupplierId;
        }
        if (productId == null) {
            return null;
        }

        List<SupplierResponse> linkedSuppliers = supplierService
                .findLinkedSuppliersByProductIds(ownerId, List.of(productId))
                .getOrDefault(productId, List.of());
        if (linkedSuppliers.size() != 1) {
            return null;
        }

        Long onlySupplierId = linkedSuppliers.getFirst().getId();
        return activeSupplierIds.contains(onlySupplierId) ? onlySupplierId : null;
    }

    private CreatePurchaseRequest newPurchaseRequest(BusinessSetupResponse setup, Long supplierId, Long productId) {
        CreatePurchaseRequest request = new CreatePurchaseRequest();
        request.setBranchId(setup.branchId());
        request.setSupplierId(supplierId);
        request.setPurchaseDate(LocalDate.now());
        request.setIdempotencyKey(UUID.randomUUID().toString());

        PurchaseItemRequest item = new PurchaseItemRequest();
        if (productId != null) {
            List<ProductVariantAccessResponse> productVariants = productAccessService
                    .findActiveVariantsForBranch(setup.businessId(), setup.branchId()).stream()
                    .filter(value -> productId.equals(value.productId()))
                    .toList();
            if (productVariants.size() == 1) {
                ProductVariantAccessResponse variant = productVariants.getFirst();
                item.setProductVariantId(variant.variantId());
                item.setEnteredUnitId(variant.purchaseUnitId() != null
                        ? variant.purchaseUnitId() : variant.baseInventoryUnitId());
                item.setIntendedSellingPrice(variant.sellingPrice());
            }
        }
        request.getItems().add(item);
        return request;
    }

    private CreatePurchaseRequest purchaseRequest(PurchaseResponse purchase) {
        CreatePurchaseRequest request = new CreatePurchaseRequest();
        request.setBranchId(purchase.branchId());
        request.setSupplierId(purchase.supplierId());
        request.setPurchaseDate(purchase.purchaseDate());
        request.setSupplierInvoiceReference(purchase.supplierInvoiceReference());
        request.setNotes(purchase.notes());
        request.setAttachmentReference(purchase.attachmentReference());
        request.setOrderTax(purchase.orderTax());
        request.setShippingCharges(purchase.shippingCharges());
        request.setOtherCharges(purchase.otherCharges());
        request.setDiscount(purchase.discount());
        request.setPaidAmount(BigDecimal.ZERO);
        request.setIdempotencyKey(UUID.randomUUID().toString());
        purchase.items().forEach(item -> {
            PurchaseItemRequest value = new PurchaseItemRequest();
            value.setProductVariantId(item.productVariantId());
            value.setEnteredUnitId(item.enteredUnitId());
            value.setEnteredQuantity(item.enteredQuantity());
            value.setUnitCost(item.unitCost());
            value.setIntendedSellingPrice(item.intendedSellingPrice());
            value.setItemTax(item.itemTax());
            value.setItemDiscount(item.itemDiscount());
            value.setBatchNumber(item.batchNumber());
            value.setManufacturingDate(item.manufacturingDate());
            value.setExpiryDate(item.expiryDate());
            request.getItems().add(value);
        });
        return request;
    }

    private PurchaseReturnRequest purchaseReturnRequest(PurchaseReturnResponse value) {
        PurchaseReturnRequest request = new PurchaseReturnRequest();
        request.setBranchId(value.branchId());
        request.setPurchaseId(value.purchaseId());
        request.setReferenceNumber(value.referenceNumber());
        request.setReturnDate(value.returnDate());
        request.setNotes(value.notes());
        request.setSettlementType(value.settlementType());
        request.setPaymentMethodId(value.paymentMethodId());
        request.setTransactionReference(value.transactionReference());
        request.setIdempotencyKey(UUID.randomUUID().toString());
        value.items().forEach(item -> {
            PurchaseReturnItemRequest requestItem = new PurchaseReturnItemRequest();
            requestItem.setPurchaseItemId(item.purchaseItemId());
            requestItem.setReturnUnitId(item.returnUnitId());
            requestItem.setReturnQuantity(item.enteredReturnQuantity());
            request.getItems().add(requestItem);
        });
        return request;
    }

    private void populatePurchaseForm(Model model, Long ownerId, BusinessSetupResponse setup,
                                      CreatePurchaseRequest request, Long editPurchaseId, Long preferredProductId) {
        List<SupplierResponse> activeBranchSuppliers = activeSuppliersForBranch(ownerId, setup.branchId());
        List<SupplierResponse> suppliers = activeBranchSuppliers;
        if (preferredProductId != null) {
            Set<Long> linkedSupplierIds = supplierService
                    .findLinkedSuppliersByProductIds(ownerId, List.of(preferredProductId))
                    .getOrDefault(preferredProductId, List.of()).stream()
                    .map(SupplierResponse::getId)
                    .collect(Collectors.toSet());
            suppliers = activeBranchSuppliers.stream()
                    .filter(supplier -> linkedSupplierIds.contains(supplier.getId()))
                    .toList();
        }
        List<ProductVariantAccessResponse> variantOptions = productAccessService
                .findActiveVariantsForBranch(setup.businessId(), setup.branchId());
        Map<Long, TaxRateResponse> taxRateMap = taxRateAccessService.findActive(setup.businessId()).stream()
                .collect(Collectors.toMap(TaxRateResponse::id, Function.identity(), (a, b) -> a));
        Map<Long, BigDecimal> variantStockMap = inventoryQueryService
                .findBranchStocks(setup.businessId(), setup.branchId()).stream()
                .collect(Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        BranchProductStockResponse::getAvailableBaseQuantity, (a, b) -> a));
        model.addAttribute("setup", setup);
        model.addAttribute("purchaseRequest", request);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("supplierMap", supplierMap(activeBranchSuppliers));
        model.addAttribute("recentPurchases", purchaseService.search(ownerId, setup.branchId(), null, null, null, null, null, "",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "purchaseDate").and(Sort.by(Sort.Direction.DESC, "id")))));
        model.addAttribute("selectedSupplierId", request.getSupplierId());
        model.addAttribute("purchaseDate", request.getPurchaseDate());
        model.addAttribute("purchaseIdempotencyKey", request.getIdempotencyKey());
        model.addAttribute("variantOptions", variantOptions);
        model.addAttribute("taxRateMap", taxRateMap);
        model.addAttribute("variantStockMap", variantStockMap);
        model.addAttribute("preferredProductId", preferredProductId);
        model.addAttribute("preferredProductName", preferredProductId == null ? null : variantOptions.stream()
                .filter(value -> preferredProductId.equals(value.productId()))
                .map(ProductVariantAccessResponse::productName).findFirst().orElse(null));
        model.addAttribute("barcodeOptions", productAccessService.findActiveBarcodesForBranch(setup.businessId(), setup.branchId()));
        model.addAttribute("unitMap", unitMap(setup));
        model.addAttribute("paymentMethods", activePaymentMethods(ownerId, setup.branchId()));
        model.addAttribute("editPurchaseId", editPurchaseId);
        addCashPaymentContext(model, setup);
        model.addAttribute("activePage", "add-purchase");
    }

    private void addValidationFlash(RedirectAttributes redirectAttributes, String attributeName,
                                    Object request, BindingResult bindingResult) {
        redirectAttributes.addFlashAttribute(attributeName, request);
        redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted Purchase form errors.");
        redirectAttributes.addFlashAttribute("validationErrors", bindingResult.getFieldErrors().stream()
                .map(this::validationMessage).distinct().toList());
    }

    private String validationMessage(FieldError error) {
        String field = error.getField().replaceAll("items\\[(\\d+)]", "Item $1").replace('.', ' ');
        return field + ": " + (error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
    }

    private List<SupplierResponse> activeSuppliers(Long ownerId) {
        return supplierService.findAll(ownerId, "", org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .filter(SupplierResponse::isActive).filter(s -> !s.isArchived()).toList();
    }

    private List<SupplierResponse> activeSuppliersForBranch(Long ownerId, Long branchId) {
        return supplierService.findAllForBranch(ownerId, branchId, "", org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(SupplierResponse::isActive).filter(s -> !s.isArchived()).toList();
    }

    private List<PaymentMethodResponse> activePaymentMethods(Long ownerId, Long branchId) {
        return paymentMethodService.findAll(ownerId).stream()
                .filter(method -> !method.archived())
                .filter(method -> method.status() == ConfigurationStatus.ACTIVE)
                .filter(method -> method.branchIds().contains(branchId))
                .toList();
    }

    private Map<Long, SupplierResponse> supplierMap(List<SupplierResponse> suppliers) {
        return suppliers.stream().collect(Collectors.toMap(SupplierResponse::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, ProductVariantAccessResponse> variantMap(BusinessSetupResponse setup) {
        return productAccessService.findActiveVariantsForBranch(setup.businessId(), setup.branchId()).stream()
                .collect(Collectors.toMap(ProductVariantAccessResponse::variantId, Function.identity(), (a, b) -> a));
    }


    private Map<Long, UnitResponse> unitMap(BusinessSetupResponse setup) {
        return unitAccessService.findActiveForBranch(setup.businessId(), setup.branchId()).stream()
                .collect(Collectors.toMap(UnitResponse::id, Function.identity(), (a, b) -> a));
    }

    private void addCashPaymentContext(Model model, BusinessSetupResponse setup) {
        model.addAttribute("cashLocations", cashManagementService.findCashLocations(setup.businessId(), setup.branchId()).stream()
                .filter(value -> value.getStatus() == CashLocationStatus.ACTIVE).toList());
        model.addAttribute("registers", cashManagementService.findRegisters(setup.businessId(), setup.branchId()).stream()
                .filter(value -> value.getStatus() == RegisterStatus.ACTIVE).toList());
        model.addAttribute("openShifts", cashManagementService.findShifts(setup.businessId(), setup.branchId()).stream()
                .filter(value -> value.getStatus() == CashierShiftStatus.OPEN).toList());
    }

    private Map<Long, ProductBatchResponse> purchaseBatchMap(BusinessSetupResponse setup, PurchaseResponse purchase) {
        return purchase.items().stream()
                .map(PurchaseItemResponse::productBatchId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(batchId -> inventoryQueryService.findBatch(setup.businessId(), purchase.branchId(), batchId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(ProductBatchResponse::getId, Function.identity()));
    }

    private int safeSize(int size) { return ALLOWED_PAGE_SIZES.contains(size) ? size : 10; }

    private String firstValidationMessage(BindingResult bindingResult) {
        return bindingResult.getAllErrors().isEmpty() ? "Please review the form and try again."
                : bindingResult.getAllErrors().getFirst().getDefaultMessage();
    }

    private String purchaseFailureRedirect(Long supplierId, String redirectTo) {
        if ("purchases".equalsIgnoreCase(redirectTo)) {
            return supplierId == null ? "redirect:/owner/purchases/new" : "redirect:/owner/purchases/new?supplierId=" + supplierId;
        }
        return supplierPurchase(supplierId);
    }

    private String purchaseReturnFailureRedirect(Long purchaseId, Long returnId) {
        if (purchaseId == null) {
            return returnId == null
                    ? "redirect:/owner/purchases/returns"
                    : "redirect:/owner/purchases/returns/" + returnId;
        }
        String base = "redirect:/owner/purchases/returns/new?purchaseId=" + purchaseId;
        return returnId == null ? base : base + "&returnId=" + returnId;
    }

    private String supplierPurchase(Long supplierId) {
        return supplierId == null ? "redirect:/owner/suppliers" : "redirect:/owner/suppliers/" + supplierId + "/purchase";
    }
}
