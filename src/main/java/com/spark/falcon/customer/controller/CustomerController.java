package com.spark.falcon.customer.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.customer.dto.CustomerRequest;
import com.spark.falcon.customer.dto.CustomerResponse;
import com.spark.falcon.customer.exception.CustomerAccessDeniedException;
import com.spark.falcon.customer.exception.CustomerNotFoundException;
import com.spark.falcon.customer.exception.CustomerOperationNotAllowedException;
import com.spark.falcon.customer.service.CustomerService;
import com.spark.falcon.customer.service.CustomerFinancialReadService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.payment.service.PaymentService;
import com.spark.falcon.sale.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/owner/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final BranchContextService branchContextService;
    private final SaleService saleService;
    private final PaymentService paymentService;
    private final CustomerFinancialReadService customerFinancialReadService;
    private final com.spark.falcon.customer.service.CustomerListReadService customerListReadService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(required = false) Boolean archived,
                       @RequestParam(required = false) Boolean hasDue,
                       @RequestParam(required = false) Boolean overdue,
                       @RequestParam(required = false) java.time.LocalDate createdFrom,
                       @RequestParam(required = false) java.time.LocalDate createdTo,
                       @RequestParam(required = false) java.time.LocalDate lastPurchaseFrom,
                       @RequestParam(required = false) java.time.LocalDate lastPurchaseTo,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "created-desc") String sort,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);

        model.addAttribute("setup", setup);
        var rowPage = customerListReadService.find(principal.ownerId(), setup.branchId(),
                new com.spark.falcon.customer.dto.CustomerListFilter(q, archived, hasDue, overdue,
                        createdFrom, createdTo, lastPurchaseFrom, lastPurchaseTo, sort),
                PageRequest.of(safePage, safeSize), java.time.LocalDate.now());
        var customerPage = rowPage.map(com.spark.falcon.customer.dto.CustomerListRowResponse::customer);
        model.addAttribute("customerPage", customerPage);
        java.util.Map<Long, com.spark.falcon.customer.dto.CustomerFinancialSummaryResponse> financialSummaries =
                rowPage.getContent().stream().collect(java.util.stream.Collectors.toMap(
                        row -> row.customer().id(), com.spark.falcon.customer.dto.CustomerListRowResponse::financial));
        model.addAttribute("financialSummaries", financialSummaries);
        model.addAttribute("q", q);
        model.addAttribute("archived", archived);
        model.addAttribute("hasDue", hasDue);
        model.addAttribute("overdue", overdue);
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        model.addAttribute("lastPurchaseFrom", lastPurchaseFrom);
        model.addAttribute("lastPurchaseTo", lastPurchaseTo);
        model.addAttribute("sort", sort);
        model.addAttribute("activePage", "customer-list");
        return "customer/customer-list";
    }

    @GetMapping("/add")
    public String addPage(@AuthenticationPrincipal OwnerPrincipal principal, Model model) {
        model.addAttribute("setup", setup(principal.ownerId()));
        if (!model.containsAttribute("customerRequest")) {
            model.addAttribute("customerRequest", new CustomerRequest());
        }
        model.addAttribute("activePage", "customer-add");
        return "customer/add-customer";
    }

    @GetMapping("/duplicates")
    @ResponseBody
    public java.util.List<com.spark.falcon.customer.dto.CustomerDuplicateCandidateResponse> duplicates(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long excludeCustomerId,
            @AuthenticationPrincipal OwnerPrincipal principal) {
        return customerService.findProbableDuplicates(
                principal.ownerId(), phone, email, excludeCustomerId);
    }

    @GetMapping("/{customerId}")
    public String details(@PathVariable Long customerId,
                          @RequestParam(required = false) Long branchId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        CustomerResponse customer = customerService.findByOwnerAndId(principal.ownerId(), customerId)
                .orElseThrow(CustomerNotFoundException::new);
        PageRequest recentPage = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        model.addAttribute("setup", setup);
        model.addAttribute("customer", customer);
        model.addAttribute("financialSummary", customerFinancialReadService.summarize(
                principal.ownerId(), activeBranchId, customerId, java.time.LocalDate.now()));
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("recentSales", saleService.findSellList(
                principal.ownerId(), activeBranchId, customerId, "ALL", null, recentPage).getContent());
        model.addAttribute("payments", paymentService.findCustomerPayments(
                principal.ownerId(), activeBranchId, customerId));
        model.addAttribute("saleReturns", saleService.findReturnList(
                principal.ownerId(), activeBranchId, customerId, null, null, recentPage).getContent());
        model.addAttribute("activePage", "customer-list");
        return "customer/customer-details";
    }

    @GetMapping("/{customerId}/edit")
    public String editPage(@PathVariable Long customerId,
                           @AuthenticationPrincipal OwnerPrincipal principal,
                           Model model) {
        CustomerResponse customer = customerService.findByOwnerAndId(principal.ownerId(), customerId)
                .orElseThrow(CustomerNotFoundException::new);

        model.addAttribute("setup", setup(principal.ownerId()));
        model.addAttribute("customer", customer);
        if (!model.containsAttribute("customerRequest")) {
            model.addAttribute("customerRequest", toRequest(customer));
        }
        model.addAttribute("activePage", "customer-list");
        return "customer/edit-customer";
    }


    @GetMapping("/due-invoices")
    public String dueInvoices(@RequestParam(required = false) Long branchId,
                              @RequestParam(required = false) Long customerId,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) java.time.LocalDate invoiceFrom,
                              @RequestParam(required = false) java.time.LocalDate invoiceTo,
                              @RequestParam(required = false) java.time.LocalDate dueFrom,
                              @RequestParam(required = false) java.time.LocalDate dueTo,
                              @RequestParam(required = false) String agingBucket,
                              @RequestParam(defaultValue = "newest") String sort,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @AuthenticationPrincipal OwnerPrincipal principal,
                              Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        model.addAttribute("setup", setup);
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("customerId", customerId);
        model.addAttribute("status", status);
        model.addAttribute("invoiceFrom", invoiceFrom);
        model.addAttribute("invoiceTo", invoiceTo);
        model.addAttribute("dueFrom", dueFrom);
        model.addAttribute("dueTo", dueTo);
        model.addAttribute("agingBucket", agingBucket);
        model.addAttribute("sort", sort);
        java.util.List<com.spark.falcon.sale.dto.CustomerDueInvoiceResponse> invoiceRows = new java.util.ArrayList<>(
                customerFinancialReadService.invoices(
                        principal.ownerId(), activeBranchId, customerId, java.time.LocalDate.now(),
                        new com.spark.falcon.customer.dto.CustomerDueInvoiceFilter(
                                status, invoiceFrom, invoiceTo, dueFrom, dueTo, agingBucket)));
        java.util.Comparator<com.spark.falcon.sale.dto.CustomerDueInvoiceResponse> invoiceSort = switch (sort.toLowerCase()) {
            case "oldest" -> java.util.Comparator.comparing(com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::createdAt);
            case "highest-due" -> java.util.Comparator.comparing(
                    com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::dueAmount).reversed();
            default -> java.util.Comparator.comparing(
                    com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::createdAt).reversed();
        };
        invoiceRows.sort(invoiceSort.thenComparing(com.spark.falcon.sale.dto.CustomerDueInvoiceResponse::id));
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * safeSize, invoiceRows.size());
        int toIndex = Math.min(fromIndex + safeSize, invoiceRows.size());
        model.addAttribute("dueInvoices", new org.springframework.data.domain.PageImpl<>(
                invoiceRows.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), invoiceRows.size()));
        model.addAttribute("activePage", "customer-due");
        return "customer/customer-due-invoices";
    }

    @GetMapping("/{customerId}/statement")
    public String statement(@PathVariable Long customerId,
                            @RequestParam(required = false) Long branchId,
                            @RequestParam(required = false) java.time.LocalDate from,
                            @RequestParam(required = false) java.time.LocalDate to,
                            @AuthenticationPrincipal OwnerPrincipal principal,
                            Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        CustomerResponse customer = customerService.findByOwnerAndId(principal.ownerId(), customerId)
                .orElseThrow(CustomerNotFoundException::new);
        model.addAttribute("setup", setup);
        model.addAttribute("customer", customer);
        model.addAttribute("branchId", activeBranchId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("recentSales", saleService.findSellList(
                principal.ownerId(), activeBranchId, customerId, "ALL", null,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
        model.addAttribute("payments", paymentService.findCustomerPayments(
                principal.ownerId(), activeBranchId, customerId));
        model.addAttribute("statementEntries", customerFinancialReadService.statement(
                principal.ownerId(), activeBranchId, customerId,
                from == null ? null : from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        model.addAttribute("activePage", "customer-statement");
        return "customer/customer-statement";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("customerRequest") CustomerRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("setup", setup(principal.ownerId()));
            model.addAttribute("activePage", "customer-add");
            return "customer/add-customer";
        }

        customerService.create(principal.ownerId(), request);
        redirectAttributes.addFlashAttribute("successMessage", "Customer created successfully.");
        return "redirect:/owner/customers";
    }

    @PostMapping("/{customerId}")
    public String update(@PathVariable Long customerId,
                         @Valid @ModelAttribute("customerRequest") CustomerRequest request,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("setup", setup(principal.ownerId()));
            model.addAttribute("customer", customerService.findByOwnerAndId(principal.ownerId(), customerId)
                    .orElseThrow(CustomerNotFoundException::new));
            model.addAttribute("activePage", "customer-list");
            return "customer/edit-customer";
        }

        try {
            customerService.update(principal.ownerId(), customerId, request);
            redirectAttributes.addFlashAttribute("successMessage", "Customer updated successfully.");
        } catch (CustomerOperationNotAllowedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/customers/" + customerId;
    }

    @PostMapping("/{customerId}/archive")
    public String archive(@PathVariable Long customerId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        try {
            customerService.archive(principal.ownerId(), customerId);
            redirectAttributes.addFlashAttribute("successMessage", "Customer archived.");
        } catch (CustomerOperationNotAllowedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/customers";
    }

    @PostMapping("/{customerId}/restore")
    public String restore(@PathVariable Long customerId,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        try {
            customerService.restore(principal.ownerId(), customerId);
            redirectAttributes.addFlashAttribute("successMessage", "Customer restored.");
        } catch (CustomerOperationNotAllowedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/customers";
    }

    @PostMapping("/{customerId}/delete")
    public String permanentlyDelete(@PathVariable Long customerId,
                                    @AuthenticationPrincipal OwnerPrincipal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            customerService.permanentlyDeleteUnused(principal.ownerId(), customerId);
            redirectAttributes.addFlashAttribute("successMessage", "Unused customer permanently deleted.");
        } catch (CustomerOperationNotAllowedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/owner/customers";
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private CustomerRequest toRequest(CustomerResponse customer) {
        CustomerRequest request = new CustomerRequest();
        request.setName(customer.name());
        request.setPhone(customer.phone());
        request.setEmail(customer.email());
        request.setGender(customer.gender());
        request.setDateOfBirth(customer.dateOfBirth());
        request.setAge(customer.age());
        request.setAddress(customer.address());
        request.setCity(customer.city());
        request.setStateDivision(customer.stateDivision());
        request.setCountry(customer.country());
        request.setNotes(customer.notes());
        request.setActive(customer.active() && !customer.archived());
        return request;
    }

    private Sort customerSort(String requested) {
        if (requested == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return switch (requested.trim().toLowerCase()) {
            case "created-asc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "name-asc" -> Sort.by(Sort.Direction.ASC, "name");
            case "name-desc" -> Sort.by(Sort.Direction.DESC, "name");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private String firstError(BindingResult result) {
        return result.getAllErrors().isEmpty()
                ? "Please review the form and try again."
                : result.getAllErrors().getFirst().getDefaultMessage();
    }
}
