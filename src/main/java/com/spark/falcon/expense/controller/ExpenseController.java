package com.spark.falcon.expense.controller;

import com.spark.falcon.businesssetup.dto.response.BusinessSetupResponse;
import com.spark.falcon.branch.service.BranchContextService;
import com.spark.falcon.cashmanagement.service.CashManagementService;
import com.spark.falcon.expense.dto.ExpenseListFilter;
import com.spark.falcon.expense.dto.ExpenseRequest;
import com.spark.falcon.expense.dto.ExpenseResponse;
import com.spark.falcon.expense.dto.RecoverableReceiptRequest;
import com.spark.falcon.expense.entity.ExpenseClassification;
import com.spark.falcon.expense.entity.ExpenseStatus;
import com.spark.falcon.expense.entity.RecoverableReceipt;
import com.spark.falcon.expense.service.ExpenseCategoryService;
import com.spark.falcon.expense.service.ExpenseService;
import com.spark.falcon.identity.security.OwnerPrincipal;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/owner/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ExpenseCategoryService categoryService;
    private final BranchContextService branchContextService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final CashManagementService cashManagementService;

    @GetMapping
    public String list(@RequestParam(required = false) Long branchId,
                       @RequestParam(required = false) Long expenseId,
                       @RequestParam(required = false) ExpenseClassification classification,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long paymentMethodId,
                       @RequestParam(required = false) ExpenseStatus status,
                       @RequestParam(required = false) Long createdBy,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String query,
                       @PageableDefault(size = 25, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        if (expenseDate != null) {
            from = expenseDate;
            to = expenseDate;
        }

        common(model, principal.ownerId(), setup, activeBranchId);
        model.addAttribute("activePage", "expense-list");
        ExpenseListFilter filter = new ExpenseListFilter(expenseId, classification, categoryId, paymentMethodId,
                status, createdBy, from, to, query);
        var page = expenseService.search(principal.ownerId(), activeBranchId, filter, pageable);
        model.addAttribute("expensePage", page);
        model.addAttribute("expenses", page.getContent());

        model.addAttribute("filterExpenseId", expenseId);
        model.addAttribute("filterClassification", classification);
        model.addAttribute("filterCategoryId", categoryId);
        model.addAttribute("filterPaymentMethodId", paymentMethodId);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterCreatedBy", createdBy);
        model.addAttribute("filterExpenseDate", expenseDate);
        model.addAttribute("filterFrom", from);
        model.addAttribute("filterTo", to);
        model.addAttribute("filterQuery", query);
        model.addAttribute("sortParam", pageable.getSort().stream().findFirst()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .orElse("expenseDate,desc"));
        return "expense/expense-list";
    }

    @GetMapping("/add")
    public String add(@RequestParam(required = false) Long branchId,
                      @AuthenticationPrincipal OwnerPrincipal principal,
                      Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        common(model, principal.ownerId(), setup, activeBranchId);
        model.addAttribute("activePage", "add-expense");

        if (!model.containsAttribute("expenseRequest")) {
            ExpenseRequest request = new ExpenseRequest();
            request.setBranchId(activeBranchId);
            request.setExpenseDate(LocalDate.now());
            request.setClassification(ExpenseClassification.OPERATING_EXPENSE);
            request.setIdempotencyKey(UUID.randomUUID().toString());
            model.addAttribute("expenseRequest", request);
        }
        return "expense/add-expense";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        ExpenseResponse expense = expenseService.find(principal.ownerId(), id);
        BusinessSetupResponse setup = setup(principal.ownerId());
        common(model, principal.ownerId(), setup, expense.branchId());
        model.addAttribute("activePage", "expense-list");
        model.addAttribute("expense", expense);
        model.addAttribute("auditTimeline", expenseService.auditTimeline(principal.ownerId(), id));

        List<RecoverableReceipt> receipts = expenseService.recoverableReceipts(principal.ownerId(), id);
        BigDecimal recoveredAmount = receipts.stream()
                .map(RecoverableReceipt::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = expense.amount().subtract(recoveredAmount).max(BigDecimal.ZERO);
        model.addAttribute("recoverableReceipts", receipts);
        model.addAttribute("recoveredAmount", recoveredAmount);
        model.addAttribute("recoverableOutstanding", outstanding);
        model.addAttribute("reversalIdempotencyKey", UUID.randomUUID().toString());
        model.addAttribute("recoverIdempotencyKey", UUID.randomUUID().toString());

        if (expense.status() == ExpenseStatus.DRAFT && !model.containsAttribute("expenseRequest")) {
            model.addAttribute("expenseRequest", requestFrom(expense));
        }
        return "expense/expense-details";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute ExpenseRequest request,
                         BindingResult result,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", first(result));
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "expenseRequest", result);
            redirectAttributes.addFlashAttribute("expenseRequest", request);
            return "redirect:/owner/expenses/add?branchId=" + request.getBranchId();
        }
        ExpenseResponse expense = expenseService.createDraft(principal.ownerId(), request);
        redirectAttributes.addFlashAttribute("successMessage", "Expense draft created successfully.");
        return "redirect:/owner/expenses/" + expense.id();
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute ExpenseRequest request,
                         BindingResult result,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", first(result));
            redirectAttributes.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "expenseRequest", result);
            redirectAttributes.addFlashAttribute("expenseRequest", request);
            return "redirect:/owner/expenses/" + id;
        }
        expenseService.updateDraft(principal.ownerId(), id, request);
        redirectAttributes.addFlashAttribute("successMessage", "Expense draft updated.");
        return back(id);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        expenseService.deleteDraft(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense draft deleted.");
        return "redirect:/owner/expenses";
    }

    @PostMapping("/{id}/submit")
    public String submit(@PathVariable Long id,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        expenseService.submit(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense submitted for approval.");
        return back(id);
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        expenseService.approve(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense approved.");
        return back(id);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        expenseService.reject(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense rejected.");
        return back(id);
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @AuthenticationPrincipal OwnerPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        expenseService.cancel(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense cancelled.");
        return back(id);
    }

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id,
                       @AuthenticationPrincipal OwnerPrincipal principal,
                       RedirectAttributes redirectAttributes) {
        expenseService.post(principal.ownerId(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Expense posted successfully.");
        return back(id);
    }

    @PostMapping("/{id}/reverse")
    public String reverse(@PathVariable Long id,
                          @RequestParam String reason,
                          @RequestParam String idempotencyKey,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        expenseService.reverse(principal.ownerId(), id, reason, idempotencyKey);
        redirectAttributes.addFlashAttribute("successMessage", "Expense reversal completed.");
        return back(id);
    }

    @PostMapping("/{id}/recover")
    public String recover(@PathVariable Long id,
                          @Valid @ModelAttribute RecoverableReceiptRequest request,
                          BindingResult result,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", first(result));
            return back(id);
        }
        expenseService.receiveRecoverable(principal.ownerId(), id, request);
        redirectAttributes.addFlashAttribute("successMessage", "Recoverable amount received and recorded.");
        return back(id);
    }

    @GetMapping("/monthwise")
    public String monthwise(@RequestParam(required = false) Long branchId,
                            @RequestParam(required = false) YearMonth month,
                            @AuthenticationPrincipal OwnerPrincipal principal,
                            Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        YearMonth selectedMonth = month == null ? YearMonth.now() : month;
        common(model, principal.ownerId(), setup, activeBranchId);
        model.addAttribute("activePage", "expense-monthwise");
        model.addAttribute("selectedMonth", selectedMonth);
        var report = expenseService.monthwise(principal.ownerId(), activeBranchId, selectedMonth);
        Map<LocalDate, BigDecimal> dailyTotals = new LinkedHashMap<>();
        report.dailyCategoryAmounts().forEach((date, amounts) ->
                dailyTotals.put(date, amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        model.addAttribute("dailyTotals", dailyTotals);
        model.addAttribute("report", report);
        return "expense/expense-monthwise";
    }

    @GetMapping("/summary")
    public String summary(@RequestParam(required = false) Long branchId,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @AuthenticationPrincipal OwnerPrincipal principal,
                          Model model) {
        BusinessSetupResponse setup = setup(principal.ownerId());
        Long activeBranchId = branchId == null ? setup.branchId() : branchId;
        LocalDate safeFrom = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate safeTo = to == null ? LocalDate.now() : to;
        common(model, principal.ownerId(), setup, activeBranchId);
        model.addAttribute("activePage", "expense-summary");
        model.addAttribute("filterFrom", safeFrom);
        model.addAttribute("filterTo", safeTo);
        model.addAttribute("summary", expenseService.summary(principal.ownerId(), activeBranchId, safeFrom, safeTo));
        return "expense/expense-summary";
    }

    private void common(Model model,
                        Long ownerId,
                        BusinessSetupResponse setup,
                        Long branchId) {
        model.addAttribute("setup", setup);
        model.addAttribute("branchId", branchId);
        model.addAttribute("categories", categoryService.list(ownerId));
        model.addAttribute("paymentMethods", paymentMethodAccessService.findActiveForBranch(setup.businessId(), branchId));
        model.addAttribute("cashLocations", cashManagementService.findCashLocations(setup.businessId(), branchId));
        model.addAttribute("registers", cashManagementService.findRegisters(setup.businessId(), branchId));
        model.addAttribute("cashierShifts", cashManagementService.findShifts(setup.businessId(), branchId));
        model.addAttribute("expenseStatuses", ExpenseStatus.values());
    }

    private BusinessSetupResponse setup(Long ownerId) {
        return branchContextService.resolveOwnerSetup(ownerId);
    }

    private ExpenseRequest requestFrom(ExpenseResponse expense) {
        ExpenseRequest request = new ExpenseRequest();
        request.setBranchId(expense.branchId());
        request.setExpenseDate(expense.expenseDate());
        request.setClassification(expense.classification());
        request.setCategoryId(expense.categoryId());
        request.setTitle(expense.title());
        request.setAmount(expense.amount());
        request.setPaymentMethodId(expense.paymentMethodId());
        request.setPaymentReference(expense.paymentReference());
        request.setPaidFromReference(expense.paidFrom());
        request.setCashLocationId(expense.cashLocationId());
        request.setRegisterId(expense.registerId());
        request.setCashierShiftId(expense.cashierShiftId());
        request.setPayee(expense.payee());
        request.setAttachmentReference(expense.attachmentReference());
        request.setNotes(expense.notes());
        request.setIdempotencyKey(UUID.randomUUID().toString());
        return request;
    }

    private String back(Long id) {
        return "redirect:/owner/expenses/" + id;
    }

    private String first(BindingResult result) {
        return result.getFieldErrors().isEmpty()
                ? "Invalid Expense"
                : result.getFieldErrors().getFirst().getDefaultMessage();
    }
}
