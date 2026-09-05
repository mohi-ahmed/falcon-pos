package com.spark.falcon.supplier.controller;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.inventory.dto.BranchProductStockResponse;
import com.spark.falcon.inventory.service.InventoryQueryService;
import com.spark.falcon.product.dto.response.ProductResponse;
import com.spark.falcon.product.dto.response.ProductVariantAccessResponse;
import com.spark.falcon.product.service.ProductAccessService;
import com.spark.falcon.product.service.ProductService;
import com.spark.falcon.purchase.dto.response.PurchaseResponse;
import com.spark.falcon.purchase.service.PurchaseService;
import com.spark.falcon.settings.dto.response.TaxRateResponse;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import com.spark.falcon.settings.service.PaymentMethodService;
import com.spark.falcon.settings.service.TaxRateAccessService;
import com.spark.falcon.supplier.dto.SupplierExpiryPerformanceResponse;
import com.spark.falcon.supplier.dto.SupplierRequest;
import com.spark.falcon.supplier.dto.SupplierResponse;
import com.spark.falcon.supplier.exception.SupplierNotFoundException;
import com.spark.falcon.supplier.service.SupplierService;
import com.spark.falcon.supplier.service.SupplierFinancialReadService;
import com.spark.falcon.shared.export.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/owner/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(10, 25, 50, 100);

    private final SupplierService supplierService;
    private final BranchContextService branchContextService;
    private final ProductService productService;
    private final ProductAccessService productAccessService;
    private final PaymentMethodService paymentMethodService;
    private final TaxRateAccessService taxRateAccessService;
    private final InventoryQueryService inventoryQueryService;
    private final PurchaseService purchaseService;
    private final SupplierFinancialReadService supplierFinancialReadService;
    private final ExportResponse exportResponse;

    @GetMapping("/export")
    public void export(@RequestParam String format,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "created-desc") String sort,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       HttpServletResponse response) throws java.io.IOException {
        BusinessSetupResponse setup = setup(principal.ownerId());
        String query = q == null ? "" : q.trim();
        ExportDocument document = new ExportDocument("Supplier List", setup.businessName(), setup.branchName(),
                ZoneId.systemDefault(), query.isBlank() ? Map.of() : Map.of("Search", query),
                List.of(ExportColumn.number("ID"), ExportColumn.text("Supplier"), ExportColumn.text("Mobile"),
                        ExportColumn.number("Total Supplied Products"), ExportColumn.dateTime("Created Date"),
                        ExportColumn.text("Status")),
                consumer -> {
                    int page = 0;
                    Page<SupplierResponse> values;
                    do {
                        values = supplierService.findAllForBranch(principal.ownerId(), setup.branchId(), query,
                                PageRequest.of(page++, 500, supplierSort(sort)));
                        for (SupplierResponse supplier : values.getContent()) {
                            consumer.accept(supplier.getId(), supplier.getName(), supplier.getMobileNumber(),
                                    supplier.getTotalSuppliedProducts(), supplier.getCreatedAt(),
                                    supplier.isActive() ? "ACTIVE" : "INACTIVE");
                        }
                    } while (values.hasNext());
                });
        exportResponse.write(format, document, response);
    }

    @GetMapping
    public String supplierList(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "created-desc") String sort,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               Model model) {
        Long ownerId = principal.ownerId();
        BusinessSetupResponse setup = setup(ownerId);
        List<BranchAccessResponse> branches = branchContextService.findOwnerSelectableBranches(ownerId);
        int safePage = Math.max(0, page);
        int safeSize = ALLOWED_PAGE_SIZES.contains(size) ? size : 10;
        String query = q == null ? "" : q.trim();

        Page<SupplierResponse> suppliers = supplierService.findAllForBranch(
                ownerId,
                setup.branchId(),
                query,
                PageRequest.of(safePage, safeSize, supplierSort(sort))
        );

        if (!model.containsAttribute("supplierRequest")) {
            SupplierRequest request = new SupplierRequest();
            request.getBranchIds().add(setup.branchId());
            model.addAttribute("supplierRequest", request);
        }
        model.addAttribute("setup", setup);
        model.addAttribute("branches", branches);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("query", query);
        model.addAttribute("selectedSize", safeSize);
        model.addAttribute("selectedSort", normalizeSortKey(sort));
        addSupplierNavigation(model, "supplier-list");
        return "supplier/supplier-list";
    }

    @GetMapping("/new")
    public String newSupplier(@AuthenticationPrincipal OwnerPrincipal principal,
                              Model model) {
        Long ownerId = principal.ownerId();
        BusinessSetupResponse setup = setup(ownerId);
        if (!model.containsAttribute("supplierRequest")) {
            SupplierRequest request = new SupplierRequest();
            request.getBranchIds().add(setup.branchId());
            model.addAttribute("supplierRequest", request);
        }
        model.addAttribute("setup", setup);
        model.addAttribute("branches", branchContextService.findOwnerSelectableBranches(ownerId));
        model.addAttribute("recentSuppliers", supplierService.findAllForBranch(
                ownerId,
                setup.branchId(),
                "",
                PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent());
        addSupplierNavigation(model, "add-supplier");
        return "supplier/add-supplier";
    }

    @GetMapping("/{supplierId}")
    public String supplierDetails(@PathVariable Long supplierId,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  Model model) {
        Long ownerId = principal.ownerId();
        BusinessSetupResponse setup = setup(ownerId);
        SupplierResponse supplier = supplierService.findByOwnerAndId(ownerId, supplierId)
                .orElseThrow(SupplierNotFoundException::new);

        Set<Long> assignedBranchIds = supplierService.findAssignedBranchIds(ownerId, supplierId);
        List<BranchAccessResponse> assignedBranches = branchContextService.findOwnerSelectableBranches(ownerId).stream()
                .filter(branch -> assignedBranchIds.contains(branch.branchId()))
                .toList();

        List<Long> linkedProductIds = supplierService.findLinkedProductIds(ownerId, supplierId);
        List<ProductResponse> products = productService.findAll(ownerId);
        List<ProductResponse> linkedProducts = products.stream()
                .filter(product -> linkedProductIds.contains(product.id()))
                .toList();
        List<ProductResponse> productOptions = products.stream()
                .filter(product -> !product.archived())
                .filter(product -> !linkedProductIds.contains(product.id()))
                .toList();

        Page<PurchaseResponse> purchaseHistory = purchaseService.search(
                ownerId,
                setup.branchId(),
                supplierId,
                null,
                null,
                null,
                null,
                "",
                PageRequest.of(0, 10,
                        Sort.by(Sort.Direction.DESC, "purchaseDate").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        SupplierExpiryPerformanceResponse expiryPerformance = supplierService.findExpiryPerformance(
                ownerId, setup.branchId(), supplierId);

        model.addAttribute("setup", setup);
        model.addAttribute("assignedBranches", assignedBranches);
        model.addAttribute("currentBranchAssigned", assignedBranchIds.contains(setup.branchId()));
        model.addAttribute("supplier", supplier);
        model.addAttribute("linkedProducts", linkedProducts);
        model.addAttribute("productOptions", productOptions);
        model.addAttribute("purchaseHistory", purchaseHistory);
        model.addAttribute("expiryPerformance", expiryPerformance);
        model.addAttribute("financialSummary", supplierFinancialReadService.summarize(
                ownerId, setup.branchId(), supplierId));
        addSupplierNavigation(model, "supplier-list");
        return "supplier/supplier-details";
    }

    @GetMapping("/{supplierId}/edit")
    public String editSupplier(@PathVariable Long supplierId,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               Model model) {
        Long ownerId = principal.ownerId();
        BusinessSetupResponse setup = setup(ownerId);
        SupplierResponse supplier = supplierService.findByOwnerAndId(ownerId, supplierId)
                .orElseThrow(SupplierNotFoundException::new);
        Set<Long> assignedBranchIds = supplierService.findAssignedBranchIds(ownerId, supplierId);

        if (!model.containsAttribute("supplierRequest")) {
            model.addAttribute("supplierRequest", toRequest(supplier, assignedBranchIds));
        }
        model.addAttribute("setup", setup);
        model.addAttribute("branches", branchContextService.findOwnerSelectableBranches(ownerId));
        model.addAttribute("supplier", supplier);
        addSupplierNavigation(model, "supplier-list");
        return "supplier/edit-supplier";
    }

    @GetMapping("/{supplierId}/purchase")
    public String supplierPurchase(@PathVariable Long supplierId,
                                   @AuthenticationPrincipal OwnerPrincipal principal,
                                   Model model) {
        Long ownerId = principal.ownerId();
        BusinessSetupResponse setup = setup(ownerId);
        SupplierResponse supplier = supplierService
                .findActiveForBranch(setup.businessId(), setup.branchId(), supplierId)
                .orElseThrow(SupplierNotFoundException::new);

        List<ProductResponse> productOptions = productService.findAll(ownerId).stream()
                .filter(product -> !product.archived())
                .toList();
        List<ProductVariantAccessResponse> variantOptions =
                productAccessService.findActiveVariantsForBranch(setup.businessId(), setup.branchId());

        model.addAttribute("setup", setup);
        model.addAttribute("supplier", supplier);
        model.addAttribute("purchaseDate", LocalDate.now());
        model.addAttribute("purchaseIdempotencyKey", UUID.randomUUID().toString());
        model.addAttribute("productOptions", productOptions);
        Map<Long, TaxRateResponse> taxRateMap = taxRateAccessService.findActive(setup.businessId()).stream()
                .collect(Collectors.toMap(TaxRateResponse::id, Function.identity(), (a, b) -> a));
        Map<Long, java.math.BigDecimal> variantStockMap = inventoryQueryService
                .findBranchStocks(setup.businessId(), setup.branchId()).stream()
                .collect(Collectors.toMap(BranchProductStockResponse::getProductVariantId,
                        BranchProductStockResponse::getAvailableBaseQuantity, (a, b) -> a));

        model.addAttribute("variantOptions", variantOptions);
        model.addAttribute("taxRateMap", taxRateMap);
        model.addAttribute("variantStockMap", variantStockMap);
        model.addAttribute("paymentMethods", paymentMethodService.findAll(ownerId).stream()
                .filter(method -> !method.archived())
                .filter(method -> method.status() == ConfigurationStatus.ACTIVE)
                .filter(method -> method.branchIds().contains(setup.branchId()))
                .toList());
        model.addAttribute("purchaseHistory", purchaseService.search(
                ownerId,
                setup.branchId(),
                supplierId,
                null,
                null,
                null,
                null,
                "",
                PageRequest.of(0, 10,
                        Sort.by(Sort.Direction.DESC, "purchaseDate").and(Sort.by(Sort.Direction.DESC, "id")))
        ));
        addSupplierNavigation(model, "supplier-list");
        return "supplier/add-purchase";
    }

    @PostMapping
    public String createSupplier(@Valid @ModelAttribute SupplierRequest request,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return validationFailure(request, bindingResult, redirectAttributes, "/owner/suppliers/new");
        }

        supplierService.create(principal.ownerId(), request);
        redirectAttributes.addFlashAttribute("successMessage", "Supplier added successfully.");
        return "redirect:/owner/suppliers";
    }

    @PostMapping("/{supplierId}")
    public String updateSupplier(@PathVariable Long supplierId,
                                 @Valid @ModelAttribute SupplierRequest request,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return validationFailure(
                    request,
                    bindingResult,
                    redirectAttributes,
                    "/owner/suppliers/" + supplierId + "/edit"
            );
        }

        supplierService.update(principal.ownerId(), supplierId, request);
        redirectAttributes.addFlashAttribute("successMessage", "Supplier updated successfully.");
        return "redirect:/owner/suppliers/" + supplierId;
    }

    @PostMapping("/{supplierId}/archive")
    public String archiveSupplier(@PathVariable Long supplierId,
                                  @AuthenticationPrincipal OwnerPrincipal principal,
                                  RedirectAttributes redirectAttributes) {
        supplierService.archive(principal.ownerId(), supplierId);
        redirectAttributes.addFlashAttribute("successMessage", "Supplier archived.");
        return "redirect:/owner/suppliers";
    }

    @PostMapping("/{supplierId}/products")
    public String assignProductFromDetails(@PathVariable Long supplierId,
                                           @RequestParam Long productId,
                                           @AuthenticationPrincipal OwnerPrincipal principal,
                                           RedirectAttributes redirectAttributes) {
        supplierService.assignProduct(principal.ownerId(), supplierId, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Product linked to supplier.");
        return "redirect:/owner/suppliers/" + supplierId;
    }

    @PostMapping("/{supplierId}/products/{productId}")
    public String assignProduct(@PathVariable Long supplierId,
                                @PathVariable Long productId,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        supplierService.assignProduct(principal.ownerId(), supplierId, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Product linked to supplier.");
        return "redirect:/owner/suppliers/" + supplierId;
    }

    @PostMapping("/{supplierId}/products/{productId}/remove")
    public String removeProduct(@PathVariable Long supplierId,
                                @PathVariable Long productId,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        supplierService.removeProduct(principal.ownerId(), supplierId, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Product removed from supplier.");
        return "redirect:/owner/suppliers/" + supplierId;
    }

    private SupplierRequest toRequest(SupplierResponse supplier, Set<Long> assignedBranchIds) {
        SupplierRequest request = new SupplierRequest();
        request.setName(supplier.getName());
        request.setCodeName(supplier.getCodeName());
        request.setEmail(supplier.getEmail());
        request.setMobileNumber(supplier.getMobileNumber());
        request.setAddress(supplier.getAddress());
        request.setCity(supplier.getCity());
        request.setState(supplier.getState());
        request.setCountry(supplier.getCountry());
        request.setAdditionalDetails(supplier.getAdditionalDetails());
        request.setActive(supplier.isActive());
        request.setDisplayOrder(supplier.getDisplayOrder());
        request.setBranchIds(new java.util.LinkedHashSet<>(assignedBranchIds));
        return request;
    }

    private Sort supplierSort(String requested) {
        return switch (normalizeSortKey(requested)) {
            case "created-asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "name-asc" -> Sort.by(Sort.Direction.ASC, "name");
            case "name-desc" -> Sort.by(Sort.Direction.DESC, "name");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private String normalizeSortKey(String requested) {
        if (requested == null) return "created-desc";
        return switch (requested.trim().toLowerCase()) {
            case "created-asc", "createdat,asc" -> "created-asc";
            case "name-asc", "name,asc" -> "name-asc";
            case "name-desc", "name,desc" -> "name-desc";
            default -> "created-desc";
        };
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private String validationFailure(SupplierRequest request,
                                     BindingResult bindingResult,
                                     RedirectAttributes redirectAttributes,
                                     String redirectPath) {
        redirectAttributes.addFlashAttribute("supplierRequest", request);
        redirectAttributes.addFlashAttribute(
                BindingResult.MODEL_KEY_PREFIX + "supplierRequest",
                bindingResult
        );
        redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted fields.");
        return "redirect:" + redirectPath;
    }

    private void addSupplierNavigation(Model model, String activePage) {
        model.addAttribute("activeSection", "suppliers");
        model.addAttribute("activePage", activePage);
    }
}
