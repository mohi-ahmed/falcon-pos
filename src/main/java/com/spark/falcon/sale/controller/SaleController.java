package com.spark.falcon.sale.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.customer.dto.CustomerAccessResponse;
import com.spark.falcon.customer.service.CustomerAccessService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.sale.dto.SaleCorrectionRequest;
import com.spark.falcon.sale.dto.SaleReturnRequest;
import com.spark.falcon.sale.entity.SaleReturnStatus;
import com.spark.falcon.sale.exception.*;
import com.spark.falcon.sale.service.SaleService;
import com.spark.falcon.sale.service.SaleReceiptEmailService;
import com.spark.falcon.sale.service.SaleInvoiceReadService;
import com.spark.falcon.pos.service.PosService;
import com.spark.falcon.pos.dto.PosRequest;
import com.spark.falcon.pos.dto.PosCartLineRequest;
import com.spark.falcon.pos.exception.PosValidationException;
import com.spark.falcon.shared.export.ExportColumn;
import com.spark.falcon.shared.export.ExportDocument;
import com.spark.falcon.shared.export.ExportResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.io.IOException;
import java.util.List;
import java.time.ZoneId;
import java.util.Map;

@Controller
@RequestMapping("/owner/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final SaleInvoiceReadService saleInvoiceReadService;
    private final BranchContextService branchContextService;
    private final PosService posService;
    private final CustomerAccessService customerAccessService;
    private final SaleReceiptEmailService saleReceiptEmailService;
    private final ExportResponse exportResponse;

    @GetMapping("/log/export")
    public void exportLog(@RequestParam(required = false) Long branchId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "newest") String sort,
                          @RequestParam String format,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          HttpServletResponse response) throws IOException {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        var rows = saleService.findSellLog(principal.ownerId(), activeBranchId, keyword,
                PageRequest.of(0, 10_000, sortForLog(sort))).getContent();
        exportResponse.write(format, new ExportDocument("Sales Log", setup.businessName(), setup.branchName(),
                ZoneId.of("UTC"), Map.of(), List.of(ExportColumn.number("Audit ID"), ExportColumn.dateTime("Date"),
                ExportColumn.text("Transaction Type"), ExportColumn.number("Sale ID"), ExportColumn.text("Customer"),
                ExportColumn.number("Actor ID"), ExportColumn.money("Amount"), ExportColumn.text("Payment Method"),
                ExportColumn.text("Details")), consumer -> {
                    for (var value : rows) consumer.accept(value.auditId(), value.createdAt(), value.transactionType(),
                            value.saleId(), displayCustomer(value.customerName(), value.customerId()), value.actorId(),
                            value.amount(), value.paymentMethod(), value.details());
                }), response);
    }

    @GetMapping("/export")
    public void exportSales(@RequestParam(required = false) Long branchId,
                            @RequestParam(required = false) Long customerId,
                            @RequestParam(defaultValue = "ALL") String filter,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "newest") String sort,
                            @RequestParam String format,
                            @AuthenticationPrincipal OwnerPrincipal principal,
                            HttpServletResponse response) throws IOException {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        var rows = saleService.findSellList(principal.ownerId(), activeBranchId, customerId, filter, keyword,
                PageRequest.of(0, 10_000, sortForSales(sort))).getContent();
        exportResponse.write(format, new ExportDocument("Sales List", setup.businessName(), setup.branchName(),
                ZoneId.of("UTC"), Map.of(), List.of(ExportColumn.number("Invoice"), ExportColumn.dateTime("Date"),
                ExportColumn.text("Customer"), ExportColumn.money("Total"), ExportColumn.money("Paid"),
                ExportColumn.money("Due"), ExportColumn.text("Payment Status"), ExportColumn.text("Sale Status")),
                consumer -> { for (var sale : rows) consumer.accept(sale.id(), sale.createdAt(),
                        displayCustomer(sale.customerName(), sale.customerId()), sale.totalPayable(), sale.paidAmount(), sale.dueAmount(), sale.paymentStatus(), sale.status()); }), response);
    }

    @GetMapping("/returns/list/export")
    public void exportReturns(@RequestParam(required = false) Long branchId,
                              @RequestParam(required = false) Long customerId,
                              @RequestParam(required = false) SaleReturnStatus status,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "newest") String sort,
                              @RequestParam String format,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              HttpServletResponse response) throws IOException {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        var rows = saleService.findReturnList(principal.ownerId(), activeBranchId, customerId, status, keyword,
                PageRequest.of(0, 10_000, sortForReturns(sort))).getContent();
        exportResponse.write(format, new ExportDocument("Sales Return List", setup.businessName(), setup.branchName(),
                ZoneId.of("UTC"), Map.of(), List.of(ExportColumn.text("Reference"), ExportColumn.number("Sale Invoice"),
                ExportColumn.dateTime("Date"), ExportColumn.text("Customer"), ExportColumn.money("Return Amount"),
                ExportColumn.money("Refund"), ExportColumn.money("Due Reduction"), ExportColumn.text("Status")),
                consumer -> { for (var item : rows) consumer.accept(item.referenceNumber(), item.saleId(), item.createdAt(),
                        displayCustomer(item.customerName(), item.customerId()), item.totalReturnAmount(), item.refundAmount(), item.dueReductionAmount(), item.status()); }), response);
    }

    @GetMapping
    public String sellList(@RequestParam(required = false) Long branchId,
                           @RequestParam(required = false) Long customerId,
                           @RequestParam(defaultValue = "ALL") String filter,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(defaultValue = "newest") String sort,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), sortForSales(sort));
        model.addAttribute("sales", saleService.findSellList(
                principal.ownerId(), activeBranchId, customerId, filter, keyword, pageable));
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("customerId", customerId);
        model.addAttribute("filter", filter);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sort", normalizeSort(sort));
        model.addAttribute("customerOptions", customerOptions(setup.businessId(), customerId));
        shell(model, setup, "sell-list");
        return "sell/sell-list";
    }


    @GetMapping("/{saleId}/edit")
    public String editPreview(@PathVariable Long saleId,
                              @RequestParam Long branchId,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        var sale = saleService.findDetails(principal.ownerId(), branchId, saleId);
        if (!sale.status().equals(com.spark.falcon.sale.entity.SaleStatus.DRAFT)
                && !sale.status().equals(com.spark.falcon.sale.entity.SaleStatus.HELD)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only a Draft or Held Sale can be edited");
            return saleRedirect(saleId, branchId);
        }
        model.addAttribute("sale", sale);
        model.addAttribute("posRequest", editRequest(sale));
        model.addAttribute("branchId", branchId);
        shell(model, principal.ownerId(), "sell-list");
        return "sell/edit-sale";
    }

    @PostMapping("/{saleId}/edit")
    public String updateDraftOrHeld(@PathVariable Long saleId,
                                    @Valid @ModelAttribute("posRequest") PosRequest request,
                                    BindingResult bindingResult,
                                    @AuthenticationPrincipal OwnerPrincipal principal,
                                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return editRedirect(saleId, request.getBranchId());
        }
        try {
            posService.updateDraftOrHeld(principal.ownerId(), saleId, request);
            redirectAttributes.addFlashAttribute("successMessage", "Draft/Held Sale updated successfully.");
            return saleRedirect(saleId, request.getBranchId());
        } catch (SaleAccessDeniedException | SaleNotFoundException | SaleValidationException |
                 SaleStateException | PosValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return editRedirect(saleId, request.getBranchId());
        }
    }

    @GetMapping("/{saleId}/invoice")
    public String invoice(@PathVariable Long saleId,
                          @RequestParam Long branchId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        model.addAttribute("invoice", saleInvoiceReadService.findInvoice(principal.ownerId(), branchId, saleId));
        return "sell/sale-invoice";
    }

    @GetMapping("/{saleId}")
    public String details(@PathVariable Long saleId,
                          @RequestParam Long branchId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        model.addAttribute("sale", saleService.findDetails(principal.ownerId(), branchId, saleId));
        SaleReturnRequest returnRequest = new SaleReturnRequest();
        returnRequest.setBranchId(branchId);
        returnRequest.setIdempotencyKey(UUID.randomUUID().toString());
        SaleCorrectionRequest correctionRequest = new SaleCorrectionRequest();
        correctionRequest.setIdempotencyKey(UUID.randomUUID().toString());

        // Reuse the existing POS/Cash Management read contracts so the Sales Return form
        // never asks users to type internal payment/cash IDs by hand. Posting validation
        // remains authoritative inside the existing Payment/Cash services.
        PosRequest cashContextRequest = new PosRequest();
        cashContextRequest.setBranchId(branchId);
        cashContextRequest.setIdempotencyKey(returnRequest.getIdempotencyKey());
        var cashContext = posService.prepareCashContext(principal.ownerId(), cashContextRequest);
        returnRequest.setCashierShiftId(cashContextRequest.getCashierShiftId());
        returnRequest.setRegisterId(cashContextRequest.getRegisterId());
        returnRequest.setCashLocationId(cashContextRequest.getCashLocationId());

        model.addAttribute("returnRequest", returnRequest);
        model.addAttribute("correctionRequest", correctionRequest);
        model.addAttribute("paymentMethods", posService.findPaymentMethods(principal.ownerId(), branchId));
        model.addAttribute("openCashierShifts", cashContext.openShifts());
        model.addAttribute("activeCashLocations", cashContext.activeCashLocations());
        shell(model, principal.ownerId(), "sell-list");
        return "sell/sell-details";
    }

    @PostMapping("/{saleId}/returns")
    public String createReturn(@PathVariable Long saleId,
                               @Valid @ModelAttribute SaleReturnRequest request,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return saleRedirect(saleId, request.getBranchId());
        }
        try {
            saleService.createReturn(principal.ownerId(), saleId, request);
            redirectAttributes.addFlashAttribute("successMessage", "Sales Return confirmed successfully.");
        } catch (SaleAccessDeniedException | SaleNotFoundException | SaleValidationException |
                 SaleStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return saleRedirect(saleId, request.getBranchId());
    }

    @GetMapping("/returns/list")
    public String returnList(@RequestParam(required = false) Long branchId,
                             @RequestParam(required = false) Long customerId,
                             @RequestParam(required = false) SaleReturnStatus status,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "newest") String sort,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             @AuthenticationPrincipal OwnerPrincipal principal,
                             Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), sortForReturns(sort));
        model.addAttribute("returns", saleService.findReturnList(
                principal.ownerId(), activeBranchId, customerId, status, keyword, pageable));
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("customerId", customerId);
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sort", normalizeSort(sort));
        model.addAttribute("customerOptions", customerOptions(setup.businessId(), customerId));
        shell(model, setup, "sell-returns");
        return "sell/sell-return-list";
    }

    @GetMapping("/returns/{returnId}")
    public String returnDetails(@PathVariable Long returnId,
                                @RequestParam Long branchId,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                Model model) {
        model.addAttribute("saleReturn", saleService.findReturnDetails(
                principal.ownerId(), branchId, returnId));
        SaleCorrectionRequest correctionRequest = new SaleCorrectionRequest();
        correctionRequest.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("correctionRequest", correctionRequest);
        shell(model, principal.ownerId(), "sell-returns");
        return "sell/sell-return-details";
    }

    @PostMapping("/returns/{returnId}/reverse")
    public String reverseReturn(@PathVariable Long returnId,
                                @RequestParam Long branchId,
                                @Valid @ModelAttribute SaleCorrectionRequest request,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal OwnerPrincipal principal,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return returnRedirect(returnId, branchId);
        }
        try {
            saleService.reverseReturn(principal.ownerId(), branchId, returnId, request);
            redirectAttributes.addFlashAttribute("successMessage", "Sales Return reversed successfully.");
        } catch (SaleAccessDeniedException | SaleReturnNotFoundException | SaleNotFoundException |
                 SaleValidationException | SaleStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return returnRedirect(returnId, branchId);
    }

    @PostMapping("/{saleId}/void")
    public String voidSale(@PathVariable Long saleId,
                           @RequestParam Long branchId,
                           @Valid @ModelAttribute SaleCorrectionRequest request,
                           BindingResult bindingResult,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           RedirectAttributes redirectAttributes) {
        return correctSale(true, saleId, branchId, request, bindingResult, principal, redirectAttributes);
    }

    @PostMapping("/{saleId}/reverse")
    public String reverseSale(@PathVariable Long saleId,
                              @RequestParam Long branchId,
                              @Valid @ModelAttribute SaleCorrectionRequest request,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              RedirectAttributes redirectAttributes) {
        return correctSale(false, saleId, branchId, request, bindingResult, principal, redirectAttributes);
    }

    @PostMapping("/{saleId}/delete")
    public String deleteDraftOrHeld(@PathVariable Long saleId,
                                    @RequestParam Long branchId,
                                    @AuthenticationPrincipal OwnerPrincipal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            saleService.deleteDraftOrHeld(principal.ownerId(), branchId, saleId);
            redirectAttributes.addFlashAttribute("successMessage", "Draft/Held Sale deleted successfully.");
        } catch (SaleAccessDeniedException | SaleNotFoundException | SaleStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/sales?branchId=" + branchId;
    }

    @GetMapping("/log")
    public String sellLog(@RequestParam(required = false) Long branchId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "newest") String sort,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), sortForLog(sort));
        model.addAttribute("logs", saleService.findSellLog(
                principal.ownerId(), activeBranchId, keyword, pageable));
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sort", normalizeSort(sort));
        shell(model, setup, "sell-log");
        return "sell/sell-log";
    }

    @PostMapping("/{saleId}/email-receipt")
    public String emailReceipt(@PathVariable Long saleId,
                               @RequestParam Long branchId,
                               @RequestParam(defaultValue = "details") String returnTo,
                               @AuthenticationPrincipal OwnerPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        try {
            String recipient = saleReceiptEmailService.send(principal.ownerId(), branchId, saleId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Receipt email sent to " + recipient + ".");
        } catch (SaleAccessDeniedException | SaleNotFoundException | SaleValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "The sale is unchanged, but the receipt email could not be sent. Check mail configuration and try again.");
        }
        if ("pos".equalsIgnoreCase(returnTo)) {
            return "redirect:/owner/pos?branchId=" + branchId + "&saleId=" + saleId;
        }
        if ("invoice".equalsIgnoreCase(returnTo)) {
            return "redirect:/owner/sales/" + saleId + "/invoice?branchId=" + branchId;
        }
        return saleRedirect(saleId, branchId);
    }

    private String correctSale(boolean voidSale,
                               Long saleId,
                               Long branchId,
                               SaleCorrectionRequest request,
                               BindingResult bindingResult,
                               OwnerPrincipal principal,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return saleRedirect(saleId, branchId);
        }
        try {
            if (voidSale) saleService.voidSale(principal.ownerId(), branchId, saleId, request);
            else saleService.reverseSale(principal.ownerId(), branchId, saleId, request);
            redirectAttributes.addFlashAttribute("successMessage",
                    voidSale ? "Sale voided successfully." : "Sale reversed successfully.");
        } catch (SaleAccessDeniedException | SaleNotFoundException | SaleValidationException |
                 SaleStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return saleRedirect(saleId, branchId);
    }


    private void shell(Model model, Long ownerId, String activePage) {
        shell(model, setup(ownerId), activePage);
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private void shell(Model model, BusinessSetupResponse setup, String activePage) {
        model.addAttribute("setup", setup);
        model.addAttribute("activePage", activePage);
    }

    private List<CustomerAccessResponse> customerOptions(Long businessId, Long selectedCustomerId) {
        java.util.LinkedHashMap<Long, CustomerAccessResponse> options = new java.util.LinkedHashMap<>();
        customerAccessService.searchActive(businessId, "", 50).forEach(value -> options.put(value.id(), value));
        if (selectedCustomerId != null) {
            customerAccessService.findByBusinessIdAndCustomerId(businessId, selectedCustomerId)
                    .ifPresent(value -> options.putIfAbsent(value.id(), value));
        }
        return List.copyOf(options.values());
    }

    private String displayCustomer(String customerName, Long customerId) {
        if (customerName != null && !customerName.isBlank()) return customerName;
        return customerId == null ? "—" : "Customer #" + customerId;
    }

    private Sort sortForSales(String value) {
        return switch (normalizeSort(value)) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            case "invoice-asc" -> Sort.by(Sort.Direction.ASC, "id");
            case "invoice-desc" -> Sort.by(Sort.Direction.DESC, "id");
            case "customer-asc" -> Sort.by(Sort.Direction.ASC, "customerId").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "customer-desc" -> Sort.by(Sort.Direction.DESC, "customerId").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "total-high" -> Sort.by(Sort.Direction.DESC, "totalPayable").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "total-low" -> Sort.by(Sort.Direction.ASC, "totalPayable").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "due-high" -> Sort.by(Sort.Direction.DESC, "dueAmount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "due-low" -> Sort.by(Sort.Direction.ASC, "dueAmount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }

    private Sort sortForReturns(String value) {
        return switch (normalizeSort(value)) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            case "customer-asc" -> Sort.by(Sort.Direction.ASC, "customerId").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "customer-desc" -> Sort.by(Sort.Direction.DESC, "customerId").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "total-high" -> Sort.by(Sort.Direction.DESC, "totalReturnAmount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "total-low" -> Sort.by(Sort.Direction.ASC, "totalReturnAmount").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }

    private Sort sortForLog(String value) {
        return switch (normalizeSort(value)) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            case "type-asc" -> Sort.by(Sort.Direction.ASC, "action").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "type-desc" -> Sort.by(Sort.Direction.DESC, "action").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "actor-asc" -> Sort.by(Sort.Direction.ASC, "actorId").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "actor-desc" -> Sort.by(Sort.Direction.DESC, "actorId").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }

    private String normalizeSort(String value) {
        if (value == null || value.isBlank()) return "newest";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "newest", "oldest", "invoice-asc", "invoice-desc", "customer-asc", "customer-desc",
                 "total-high", "total-low", "due-high", "due-low", "type-asc", "type-desc",
                 "actor-asc", "actor-desc" -> normalized;
            default -> "newest";
        };
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private String saleRedirect(Long saleId, Long branchId) {
        return "redirect:/owner/sales/" + saleId + "?branchId=" + branchId;
    }

    private String returnRedirect(Long returnId, Long branchId) {
        return "redirect:/owner/sales/returns/" + returnId + "?branchId=" + branchId;
    }

    private String editRedirect(Long saleId, Long branchId) {
        return "redirect:/owner/sales/" + saleId + "/edit?branchId=" + branchId;
    }

    private PosRequest editRequest(com.spark.falcon.sale.dto.SaleResponse sale) {
        PosRequest request = new PosRequest();
        request.setBranchId(sale.branchId());
        request.setCustomerId(sale.customerId());
        request.setOrderDiscount(sale.orderDiscount());
        request.setShippingCharge(sale.shippingCharge());
        request.setOtherCharge(sale.otherCharge());
        request.setPaidNow(java.math.BigDecimal.ZERO);
        request.setNotes(sale.notes());
        request.setIdempotencyKey("SALE-EDIT-" + sale.id() + "-" + UUID.randomUUID());
        request.setItems(sale.items().stream().map(item -> {
            PosCartLineRequest line = new PosCartLineRequest();
            line.setProductVariantId(item.productVariantId());
            line.setUnitId(item.enteredUnitId());
            line.setQuantity(item.enteredQuantity());
            line.setDiscount(item.discountAmount());
            return line;
        }).toList());
        return request;
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid Sell data" : error.getDefaultMessage())
                .orElse("Invalid Sell data");
    }
}
