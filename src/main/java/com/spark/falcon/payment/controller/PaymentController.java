package com.spark.falcon.payment.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.cashmanagement.entity.CashLocationStatus;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import com.spark.falcon.cashmanagement.entity.RegisterStatus;
import com.spark.falcon.cashmanagement.exception.CashLocationNotFoundException;
import com.spark.falcon.cashmanagement.exception.CashManagementAccessDeniedException;
import com.spark.falcon.cashmanagement.exception.CashManagementValidationException;
import com.spark.falcon.cashmanagement.exception.CashRegisterNotFoundException;
import com.spark.falcon.cashmanagement.service.CashManagementAccessService;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.payment.dto.PaymentReversalRequest;
import com.spark.falcon.payment.dto.PaymentHistoryFilter;
import com.spark.falcon.payment.entity.PaymentDirection;
import com.spark.falcon.payment.entity.PaymentStatus;
import com.spark.falcon.payment.dto.CustomerPaymentAllocationCommand;
import com.spark.falcon.payment.dto.CustomerPaymentCommand;
import com.spark.falcon.payment.dto.CustomerPaymentRequest;
import com.spark.falcon.payment.dto.SupplierPaymentAllocationCommand;
import com.spark.falcon.payment.dto.SupplierPaymentRequest;
import com.spark.falcon.payment.dto.SupplierPaymentCommand;
import com.spark.falcon.payment.exception.PaymentAccessDeniedException;
import com.spark.falcon.payment.exception.PaymentNotFoundException;
import com.spark.falcon.payment.exception.PaymentStateException;
import com.spark.falcon.payment.exception.PaymentValidationException;
import com.spark.falcon.payment.service.PaymentService;
import com.spark.falcon.purchase.dto.response.PurchaseResponse;
import com.spark.falcon.purchase.service.PurchaseService;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
import com.spark.falcon.user.service.UserAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Controller
@RequestMapping("/owner/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final BranchContextService branchContextService;
    private final BranchAccessService branchAccessService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final CashManagementAccessService cashManagementAccessService;
    private final PurchaseService purchaseService;
    private final UserAccessService userAccessService;


    @GetMapping
    public String overview(@RequestParam(required = false) Long branchId,
                           @RequestParam(required = false) Long paymentMethodId,
                           @RequestParam(required = false) PaymentDirection direction,
                           @RequestParam(required = false) PaymentStatus status,
                           @RequestParam(required = false) Long actorId,
                           @RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to,
                           @AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = resolvePaymentBranch(principal, setup, branchId);
        ZoneId zone = branchZone(setup.businessId(), activeBranchId);
        LocalDate resolvedFrom = from == null ? LocalDate.now(zone) : from;
        LocalDate resolvedTo = to == null ? resolvedFrom : to;
        model.addAttribute("setup", setup);
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("from", resolvedFrom);
        model.addAttribute("to", resolvedTo);
        model.addAttribute("paymentMethodId", paymentMethodId);
        model.addAttribute("direction", direction);
        model.addAttribute("status", status);
        model.addAttribute("actorId", actorId);
        model.addAttribute("paymentMethods", paymentMethodAccessService.findActiveForBranch(setup.businessId(), activeBranchId));
        model.addAttribute("canReceiveCustomerDue", principal.hasPermission("PAYMENT_RECEIVE_CUSTOMER_DUE"));
        model.addAttribute("canPaySupplierDue", principal.hasPermission("PAYMENT_PAY_SUPPLIER_DUE"));
        model.addAttribute("overview", paymentService.overview(
                principal.ownerId(), activeBranchId, paymentMethodId, direction, status, actorId,
                resolvedFrom.atStartOfDay(zone).toInstant(), resolvedTo.plusDays(1).atStartOfDay(zone).toInstant()));
        model.addAttribute("activePage", "payment-overview");
        return "payment/payment-overview";
    }

    @GetMapping("/customer")
    public String customerPaymentPage(@RequestParam(required = false) Long branchId,
                                      @RequestParam(required = false) Long customerId,
                                      @RequestParam(required = false) Long saleId,
                                      @AuthenticationPrincipal OwnerPrincipal principal,
                                      Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = resolvePaymentBranch(principal, setup, branchId);
        CustomerPaymentRequest request = new CustomerPaymentRequest();
        request.setBranchId(activeBranchId);
        request.setCustomerId(customerId);
        request.setPaymentDateTime(LocalDateTime.now(branchZone(setup.businessId(), activeBranchId)).withSecond(0).withNano(0));
        request.setIdempotencyKey(UUID.randomUUID().toString());
        var allocation = new com.spark.falcon.payment.dto.CustomerPaymentAllocationRequest();
        if (saleId != null) allocation.setSaleId(saleId);
        request.getAllocations().add(allocation);
        model.addAttribute("setup", setup);
        model.addAttribute("customerPaymentRequest", request);
        model.addAttribute("paymentMethods", paymentMethodAccessService.findActiveForBranch(setup.businessId(), activeBranchId));
        List<com.spark.falcon.sale.dto.SalePaymentInvoiceResponse> eligibleCustomerInvoices = customerId == null ? List.of()
                : paymentService.findEligibleCustomerInvoices(principal.ownerId(), activeBranchId, customerId);
        if (saleId != null) {
            eligibleCustomerInvoices.stream().filter(invoice -> invoice.saleId().equals(saleId)).findFirst()
                    .ifPresent(invoice -> {
                        allocation.setAmount(invoice.outstandingDue());
                        request.setAmount(invoice.outstandingDue());
                    });
        }
        model.addAttribute("eligibleCustomerInvoices", eligibleCustomerInvoices);
        model.addAttribute("customerOutstandingDue", eligibleCustomerInvoices.stream()
                .map(com.spark.falcon.sale.dto.SalePaymentInvoiceResponse::outstandingDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("existingPayments", customerId == null ? List.of() : paymentService.findCustomerPayments(
                principal.ownerId(), activeBranchId, customerId));
        model.addAttribute("canConfirmPayment", principal.hasPermission("PAYMENT_CONFIRM"));
        model.addAttribute("activePage", "receive-customer-due");
        return "payment/receive-customer-due";
    }

    @GetMapping("/supplier")
    public String supplierPaymentPage(@RequestParam(required = false) Long branchId,
                                      @RequestParam(required = false) Long supplierId,
                                      @RequestParam(required = false) List<Long> selectedPurchaseIds,
                                      @AuthenticationPrincipal OwnerPrincipal principal,
                                      Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = resolvePaymentBranch(principal, setup, branchId);
        SupplierPaymentRequest request = new SupplierPaymentRequest();
        request.setBranchId(activeBranchId);
        request.setSupplierId(supplierId);
        request.setIdempotencyKey(UUID.randomUUID().toString());
        List<PurchaseResponse> selectedPurchases = selectedPurchaseIds == null ? List.of() : selectedPurchaseIds.stream()
                .distinct()
                .map(id -> purchaseService.findByOwnerAndId(principal.ownerId(), id))
                .filter(purchase -> purchase.branchId().equals(activeBranchId))
                .filter(purchase -> purchase.dueAmount() != null && purchase.dueAmount().signum() > 0)
                .toList();
        if (!selectedPurchases.isEmpty()) {
            Long selectedSupplierId = selectedPurchases.getFirst().supplierId();
            if (selectedPurchases.stream().anyMatch(purchase -> !purchase.supplierId().equals(selectedSupplierId))) {
                throw new PaymentValidationException("Pay All purchases must belong to the same supplier");
            }
            request.setSupplierId(selectedSupplierId);
            selectedPurchases.forEach(purchase -> {
                var allocation = new com.spark.falcon.payment.dto.SupplierPaymentAllocationRequest();
                allocation.setPurchaseId(purchase.id());
                allocation.setAmount(purchase.dueAmount());
                request.getAllocations().add(allocation);
            });
            request.setAmount(selectedPurchases.stream().map(PurchaseResponse::dueAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        } else {
            request.getAllocations().add(new com.spark.falcon.payment.dto.SupplierPaymentAllocationRequest());
        }
        model.addAttribute("setup", setup);
        model.addAttribute("supplierPaymentRequest", request);
        model.addAttribute("paymentMethods", paymentMethodAccessService.findActiveForBranch(setup.businessId(), activeBranchId));
        addCashPaymentContext(model, setup.businessId(), activeBranchId);
        Long resolvedSupplierId = request.getSupplierId();
        List<com.spark.falcon.purchase.dto.response.PurchasePaymentInvoiceResponse> eligibleSupplierInvoices = resolvedSupplierId == null ? List.of()
                : paymentService.findEligibleSupplierInvoices(principal.ownerId(), activeBranchId, resolvedSupplierId);
        model.addAttribute("eligibleSupplierInvoices", eligibleSupplierInvoices);
        model.addAttribute("supplierOutstandingDue", eligibleSupplierInvoices.stream()
                .map(com.spark.falcon.purchase.dto.response.PurchasePaymentInvoiceResponse::outstandingDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("existingPayments", resolvedSupplierId == null ? List.of() : paymentService.findSupplierPayments(
                principal.ownerId(), activeBranchId, resolvedSupplierId));
        model.addAttribute("canConfirmPayment", principal.hasPermission("PAYMENT_CONFIRM"));
        model.addAttribute("activePage", "pay-supplier-due");
        return "payment/pay-supplier-due";
    }

    @GetMapping("/history")
    public String history(@RequestParam(required = false) Long branchId,
                          @RequestParam(required = false) Long paymentId,
                          @RequestParam(required = false) Long invoiceId,
                          @RequestParam(required = false) Long customerId,
                          @RequestParam(required = false) Long supplierId,
                          @RequestParam(required = false) Long paymentMethodId,
                          @RequestParam(required = false) PaymentDirection direction,
                          @RequestParam(required = false) PaymentStatus status,
                          @RequestParam(required = false) Long actorId,
                          @RequestParam(required = false) LocalDate from,
                          @RequestParam(required = false) LocalDate to,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long filterBranchId = branchId == null && principal.isStaff()
                ? setup.branchId() : branchId;
        Long zoneBranchId = filterBranchId == null ? setup.branchId() : filterBranchId;
        ZoneId zone = branchZone(setup.businessId(), zoneBranchId);
        Instant fromTime = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toTime = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        var payments = paymentService.findHistory(principal.ownerId(),
                new PaymentHistoryFilter(filterBranchId, paymentId, invoiceId, customerId, supplierId,
                        paymentMethodId, direction, status, actorId, fromTime, toTime),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("setup", setup);
        model.addAttribute("payments", payments);
        model.addAttribute("branchId", filterBranchId);
        model.addAttribute("customerId", customerId);
        model.addAttribute("supplierId", supplierId);
        model.addAttribute("paymentId", paymentId);
        model.addAttribute("invoiceId", invoiceId);
        model.addAttribute("paymentMethodId", paymentMethodId);
        model.addAttribute("direction", direction);
        model.addAttribute("status", status);
        model.addAttribute("actorId", actorId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("size", safeSize);
        model.addAttribute("canExportPayment", principal.hasPermission("PAYMENT_EXPORT"));
        model.addAttribute("canViewPaymentReference", principal.hasPermission("PAYMENT_VIEW_REFERENCE"));
        model.addAttribute("paymentMethods", paymentMethodAccessService.findActiveForBranch(setup.businessId(), zoneBranchId));
        model.addAttribute("activePage", "payment-history");
        return "payment/payment-history";
    }

    @GetMapping("/{paymentId}")
    public String details(@PathVariable Long paymentId,
                          @RequestParam(required = false) Long branchId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = resolvePaymentBranch(principal, setup, branchId);
        PaymentReversalRequest reversalRequest = new PaymentReversalRequest();
        reversalRequest.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("setup", setup);
        model.addAttribute("payment", paymentService.findById(principal.ownerId(), activeBranchId, paymentId));
        model.addAttribute("paymentReversalRequest", reversalRequest);
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("canReversePayment", principal.hasPermission("PAYMENT_REVERSE"));
        model.addAttribute("canViewPaymentReference", principal.hasPermission("PAYMENT_VIEW_REFERENCE"));
        model.addAttribute("activePage", "payment-history");
        return "payment/payment-details";
    }

    @PostMapping("/customer")
    public String receiveCustomerDue(@Valid @ModelAttribute CustomerPaymentRequest request,
                                     BindingResult bindingResult,
                                     @AuthenticationPrincipal OwnerPrincipal principal,
                                     RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return customerRedirect(request.getCustomerId(), request.getBranchId());
        }
        if (!principal.hasPermission("PAYMENT_CONFIRM")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Confirm Payment permission is required.");
            return "redirect:/owner/payments/customer?branchId=" + request.getBranchId()
                    + (request.getCustomerId() == null ? "" : "&customerId=" + request.getCustomerId());
        }
        try {
            List<CustomerPaymentAllocationCommand> allocations = request.getAllocations().stream()
                    .map(value -> new CustomerPaymentAllocationCommand(value.getSaleId(), value.getAmount()))
                    .toList();
            paymentService.receiveCustomerDue(new CustomerPaymentCommand(
                    principal.ownerId(), request.getBranchId(), request.getCustomerId(),
                    request.getPaymentDateTime() == null ? null : request.getPaymentDateTime()
                            .atZone(branchZone(setup(principal.ownerId()).businessId(), request.getBranchId())).toInstant(),
                    request.getAmount(), request.getPaymentMethodId(), request.getTransactionReference(),
                    request.getAccountReference(), request.getCashLocationId(), request.getRegisterId(),
                    request.getCashierShiftId(), allocations, request.isAutomaticOldestDueFirst(),
                    request.getExcessSettlementType(),
                    request.getIdempotencyKey(), request.getNotes(), request.getAttachmentReference()));
            redirectAttributes.addFlashAttribute("successMessage", "Customer Payment confirmed successfully.");
        } catch (PaymentAccessDeniedException | PaymentValidationException | PaymentStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return customerRedirect(request.getCustomerId(), request.getBranchId());
    }

    @PostMapping("/supplier")
    public String paySupplierDue(@Valid @ModelAttribute SupplierPaymentRequest request,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal OwnerPrincipal principal,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return purchaseListRedirect(request.getSupplierId());
        }

        if (!principal.hasPermission("PAYMENT_CONFIRM")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Confirm Payment permission is required.");
            return "redirect:/owner/payments/supplier?branchId=" + request.getBranchId()
                    + (request.getSupplierId() == null ? "" : "&supplierId=" + request.getSupplierId());
        }
        try {
            List<SupplierPaymentAllocationCommand> allocations = request.getAllocations().stream()
                    .map(value -> new SupplierPaymentAllocationCommand(value.getPurchaseId(), value.getAmount()))
                    .toList();
            paymentService.paySupplierDue(new SupplierPaymentCommand(
                    principal.ownerId(), request.getBranchId(), request.getSupplierId(), request.getPaymentMethodId(),
                    request.getAmount(), request.getTransactionReference(), request.getAccountReference(),
                    request.getCashLocationId(), request.getRegisterId(), request.getCashierShiftId(), allocations,
                    request.isAutomaticOldestDueFirst(), request.getIdempotencyKey(), request.getNotes(),
                    request.getAttachmentReference()));
            redirectAttributes.addFlashAttribute("successMessage", "Supplier Payment confirmed successfully.");
        } catch (PaymentAccessDeniedException | PaymentValidationException | PaymentStateException
                 | CashLocationNotFoundException | CashRegisterNotFoundException
                 | CashManagementValidationException | CashManagementAccessDeniedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", cashPaymentErrorMessage(exception));
        }
        return purchaseListRedirect(request.getSupplierId());
    }

    @PostMapping("/{paymentId}/reverse")
    public String reverse(@PathVariable Long paymentId,
                          @Valid @ModelAttribute PaymentReversalRequest request,
                          BindingResult bindingResult,
                          @RequestParam Long branchId,
                          @RequestParam(required = false) Long supplierId,
                          @RequestParam(required = false) Long customerId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", firstError(bindingResult));
            return paymentReturnRedirect(paymentId, branchId, customerId, supplierId);
        }

        try {
            paymentService.reversePayment(
                    principal.ownerId(), branchId, paymentId, request.getReason(), request.getIdempotencyKey());
            redirectAttributes.addFlashAttribute("successMessage", "Payment reversal confirmed successfully.");
        } catch (PaymentAccessDeniedException | PaymentNotFoundException | PaymentValidationException |
                 PaymentStateException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return paymentReturnRedirect(paymentId, branchId, customerId, supplierId);
    }


    private String paymentReturnRedirect(Long paymentId, Long branchId, Long customerId, Long supplierId) {
        if (paymentId != null && branchId != null) {
            return "redirect:/owner/payments/" + paymentId + "?branchId=" + branchId;
        }
        if (customerId != null) {
            return "redirect:/owner/customers/" + customerId + (branchId == null ? "" : "?branchId=" + branchId);
        }
        return purchaseListRedirect(supplierId);
    }

    private Long resolvePaymentBranch(OwnerPrincipal principal, BusinessSetupResponse setup, Long requestedBranchId) {
        Long branchId = requestedBranchId == null ? setup.branchId() : requestedBranchId;
        if (principal.isStaff() && !userAccessService.hasActiveBranchAccess(
                setup.businessId(), principal.staffUserId(), branchId)) {
            throw new PaymentAccessDeniedException();
        }
        return branchId;
    }

    private void addCashPaymentContext(Model model, Long businessId, Long branchId) {
        model.addAttribute("cashLocations", cashManagementAccessService.findCashLocations(businessId, branchId).stream()
                .filter(value -> value.getStatus() == CashLocationStatus.ACTIVE)
                .toList());
        model.addAttribute("registers", cashManagementAccessService.findRegisters(businessId, branchId).stream()
                .filter(value -> value.getStatus() == RegisterStatus.ACTIVE)
                .toList());
        model.addAttribute("openShifts", cashManagementAccessService.findShifts(businessId, branchId).stream()
                .filter(value -> value.getStatus() == CashierShiftStatus.OPEN)
                .toList());
    }

    private String cashPaymentErrorMessage(RuntimeException exception) {
        if (exception instanceof CashLocationNotFoundException) {
            return "Selected Cash Location is unavailable for this Branch. Choose an active Cash Location and try again.";
        }
        if (exception instanceof CashRegisterNotFoundException) {
            return "Selected Register is unavailable for this Branch. Choose an active Register and try again.";
        }
        return exception.getMessage();
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private ZoneId branchZone(Long businessId, Long branchId) {
        String timeZone = branchAccessService.findByBusinessIdAndBranchId(businessId, branchId)
                .orElseThrow(PaymentAccessDeniedException::new).timeZone();
        return ZoneId.of(timeZone);
    }

    private String purchaseListRedirect(Long supplierId) {
        return supplierId == null
                ? "redirect:/owner/purchases"
                : "redirect:/owner/purchases?supplierId=" + supplierId;
    }

    private String customerRedirect(Long customerId, Long branchId) {
        if (customerId == null) return "redirect:/owner/customers";
        String suffix = branchId == null ? "" : "?branchId=" + branchId;
        return "redirect:/owner/customers/" + customerId + suffix;
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid Payment data" : error.getDefaultMessage())
                .orElse("Invalid Payment data");
    }
}
