package com.spark.falcon.expense.service;

import com.spark.falcon.branch.service.BranchAccessService;
import com.spark.falcon.businesssetup.service.BusinessAccessService;
import com.spark.falcon.cashmanagement.dto.*;
import com.spark.falcon.cashmanagement.entity.*;
import com.spark.falcon.cashmanagement.service.CashManagementPostingService;
import com.spark.falcon.expense.dto.*;
import com.spark.falcon.expense.entity.*;
import com.spark.falcon.expense.exception.*;
import com.spark.falcon.expense.repository.*;
import com.spark.falcon.identity.security.CurrentActorService;
import com.spark.falcon.settings.dto.response.PaymentMethodResponse;
import com.spark.falcon.settings.service.PaymentMethodAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseAuditEventRepository auditRepository;
    private final RecoverableReceiptRepository recoverableReceiptRepository;
    private final ExpenseSearchRepository searchRepository;
    private final BusinessAccessService businessAccessService;
    private final BranchAccessService branchAccessService;
    private final PaymentMethodAccessService paymentMethodAccessService;
    private final CashManagementPostingService cashManagementPostingService;
    private final CurrentActorService currentActorService;
    private final Clock clock;

    @Transactional
    public ExpenseResponse createDraft(Long ownerId, ExpenseRequest request) {
        Context c = context(ownerId, request);
        Expense repeated = expenseRepository.findByBusinessIdAndBranchIdAndIdempotencyKey(c.businessId, request.getBranchId(), request.getIdempotencyKey().trim()).orElse(null);
        if (repeated != null) return response(repeated);
        Instant now = Instant.now(clock);
        Expense expense = Expense.draft(c.businessId, request.getBranchId(), request.getExpenseDate(), request.getClassification(),
                c.category == null ? null : c.category.getId(), c.category == null ? null : c.category.getName(),
                clean(request.getTitle()), money(request.getAmount()), c.method.id(), c.method.name(),
                clean(request.getPaymentReference()), clean(request.getPaidFromReference()), request.getCashLocationId(),
                request.getRegisterId(), request.getCashierShiftId(), nullable(request.getPayee()),
                nullable(request.getAttachmentReference()), nullable(request.getNotes()), actor(ownerId),
                request.getIdempotencyKey().trim(), now);
        expense = expenseRepository.saveAndFlush(expense); audit(expense, "DRAFT_CREATED", actor(ownerId), null, now);
        return response(expense);
    }

    @Transactional
    public ExpenseResponse updateDraft(Long ownerId, Long expenseId, ExpenseRequest request) {
        Context c = context(ownerId, request); Expense expense = require(c.businessId, expenseId);
        if (!expense.getBranchId().equals(request.getBranchId())) throw new ExpenseAccessDeniedException();
        Instant now = Instant.now(clock);
        try { expense.changeDraft(request.getExpenseDate(), request.getClassification(), c.category == null ? null : c.category.getId(),
                c.category == null ? null : c.category.getName(), clean(request.getTitle()), money(request.getAmount()),
                c.method.id(), c.method.name(), clean(request.getPaymentReference()), clean(request.getPaidFromReference()),
                request.getCashLocationId(), request.getRegisterId(), request.getCashierShiftId(), nullable(request.getPayee()),
                nullable(request.getAttachmentReference()), nullable(request.getNotes()), now); }
        catch (IllegalStateException e) { throw new ExpenseStateException(e.getMessage()); }
        audit(expense, "DRAFT_UPDATED", actor(ownerId), null, now); return response(expense);
    }

    @Transactional public ExpenseResponse submit(Long ownerId, Long id) { return transition(ownerId,id,"SUBMITTED", e -> e.submit(Instant.now(clock))); }
    @Transactional public ExpenseResponse approve(Long ownerId, Long id) { return transition(ownerId,id,"APPROVED", e -> e.approve(actor(ownerId), Instant.now(clock))); }
    @Transactional public ExpenseResponse reject(Long ownerId, Long id) { return transition(ownerId,id,"REJECTED", e -> e.reject(Instant.now(clock))); }
    @Transactional public ExpenseResponse cancel(Long ownerId, Long id) { return transition(ownerId,id,"CANCELLED", e -> e.cancel(Instant.now(clock))); }

    @Transactional
    public void deleteDraft(Long ownerId, Long id) {
        Long businessId = business(ownerId); Expense expense = require(businessId, id);
        if (expense.getStatus() != ExpenseStatus.DRAFT) throw new ExpenseStateException("Only Draft Expense may be deleted");
        auditRepository.deleteAll(auditRepository.findAllByExpenseIdOrderByOccurredAtAsc(id)); expenseRepository.delete(expense);
    }

    @Transactional
    public ExpenseResponse post(Long ownerId, Long id) {
        Long businessId = business(ownerId); Expense expense = require(businessId, id);
        if (expense.getStatus() != ExpenseStatus.APPROVED) throw new ExpenseStateException("Only Approved Expense may be posted");
        PaymentMethodResponse method = paymentMethodAccessService.findActiveForBranch(businessId, expense.getBranchId(), expense.getPaymentMethodId()).orElseThrow(() -> new ExpenseValidationException("Payment Method is inactive or unavailable for this branch"));
        Long movementId = null;
        if (method.cash()) {
            CashMovementRequest cash = new CashMovementRequest(); cash.setCashLocationId(expense.getCashLocationId());
            cash.setRegisterId(expense.getRegisterId()); cash.setCashierShiftId(expense.getCashierShiftId());
            cash.setSourceModule(CashSourceModule.EXPENSE); cash.setSourceTransactionId(String.valueOf(expense.getId()));
            cash.setSourceReference(expense.getPaymentReference());
            cash.setMovementType(expense.getClassification() == ExpenseClassification.OPERATING_EXPENSE ? CashMovementType.OPERATING_EXPENSE_PAYMENT : CashMovementType.RECOVERABLE_DEPOSIT_ADVANCE);
            cash.setDirection(CashMovementDirection.OUTFLOW); cash.setAmount(expense.getAmount()); cash.setPostedByUserId(actor(ownerId));
            cash.setPostingKey("expense-post-" + expense.getId()); cash.setNote(expense.getNotes());
            movementId = cashManagementPostingService.post(businessId, expense.getBranchId(), cash).getId();
        }
        Instant now = Instant.now(clock); expense.post(actor(ownerId), movementId, now); audit(expense, "POSTED", actor(ownerId), null, now);
        return response(expense);
    }

    @Transactional
    public ExpenseResponse reverse(Long ownerId, Long id, String reason, String idempotencyKey) {
        if (reason == null || reason.isBlank()) throw new ExpenseValidationException("Reversal reason is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new ExpenseValidationException("Reversal idempotency key is required");
        Long businessId = business(ownerId); Expense expense = require(businessId, id);
        if (expense.getStatus() == ExpenseStatus.REVERSED) return response(expense);
        if (expense.getStatus() != ExpenseStatus.POSTED) throw new ExpenseStateException("Only Posted Expense may be reversed");
        if (expense.getClassification() == ExpenseClassification.RECOVERABLE_DEPOSIT_ADVANCE
                && !recoverableReceiptRepository.findAllByBusinessIdAndBranchIdAndExpenseId(
                businessId, expense.getBranchId(), id).isEmpty()) {
            throw new ExpenseStateException("A Recoverable Deposit/Advance with received recovery cannot be reversed");
        }
        Long reversalMovementId = null;
        if (expense.getCashMovementId() != null) reversalMovementId = cashManagementPostingService.reverse(businessId,
                expense.getBranchId(), expense.getCashMovementId(), CashSourceModule.EXPENSE, String.valueOf(id),
                expense.getPaymentReference(), actor(ownerId), idempotencyKey.trim(), reason.trim()).getId();
        Instant now = Instant.now(clock); expense.reverse(reversalMovementId, reason.trim(), now);
        audit(expense, "REVERSED", actor(ownerId), reason.trim(), now); return response(expense);
    }

    @Transactional
    public void receiveRecoverable(Long ownerId, Long expenseId, RecoverableReceiptRequest request) {
        Long businessId=business(ownerId); Expense expense=require(businessId,expenseId);
        if(expense.getClassification()!=ExpenseClassification.RECOVERABLE_DEPOSIT_ADVANCE || expense.getStatus()!=ExpenseStatus.POSTED) throw new ExpenseStateException("Only a Posted Recoverable Deposit/Advance can receive recovery");
        String key=request.getIdempotencyKey().trim(); if(recoverableReceiptRepository.findByBusinessIdAndIdempotencyKey(businessId,key).isPresent()) return;
        BigDecimal amount=money(request.getAmount()); BigDecimal received=recoverableReceiptRepository.findAllByBusinessIdAndBranchIdAndExpenseId(businessId,expense.getBranchId(),expenseId).stream().map(RecoverableReceipt::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(received.add(amount).compareTo(expense.getAmount())>0) throw new ExpenseValidationException("Recovery amount cannot exceed outstanding recoverable balance");
        PaymentMethodResponse method=paymentMethodAccessService.findActiveForBranch(businessId,expense.getBranchId(),request.getPaymentMethodId()).orElseThrow(()->new ExpenseValidationException("Payment Method is inactive or unavailable for this branch"));
        Long movementId=null; if(method.cash()) { if(request.getCashLocationId()==null||request.getRegisterId()==null||request.getCashierShiftId()==null) throw new ExpenseValidationException("Cash recovery requires Cash Location, Register and active Cashier Shift"); CashMovementRequest cash=new CashMovementRequest();cash.setCashLocationId(request.getCashLocationId());cash.setRegisterId(request.getRegisterId());cash.setCashierShiftId(request.getCashierShiftId());cash.setSourceModule(CashSourceModule.EXPENSE);cash.setSourceTransactionId(String.valueOf(expenseId));cash.setSourceReference(request.getReceiptReference().trim());cash.setMovementType(CashMovementType.RECOVERABLE_AMOUNT_RECEIVED);cash.setDirection(CashMovementDirection.INFLOW);cash.setAmount(amount);cash.setPostedByUserId(actor(ownerId));cash.setPostingKey("expense-recovery-"+key);movementId=cashManagementPostingService.post(businessId,expense.getBranchId(),cash).getId(); }
        recoverableReceiptRepository.save(RecoverableReceipt.create(businessId,expense.getBranchId(),expenseId,amount,method.id(),method.name(),request.getReceiptReference().trim(),request.getCashLocationId(),request.getRegisterId(),request.getCashierShiftId(),movementId,actor(ownerId),key,Instant.now(clock)));
        audit(expense,"RECOVERABLE_AMOUNT_RECEIVED",actor(ownerId),"Amount: "+amount,Instant.now(clock));
    }

    @Transactional(readOnly=true)
    public ExpenseResponse find(Long ownerId, Long id) { return response(require(business(ownerId), id)); }
    @Transactional(readOnly=true) public List<ExpenseAuditEvent> auditTimeline(Long ownerId,Long id){Expense e=require(business(ownerId),id);return auditRepository.findAllByExpenseIdOrderByOccurredAtAsc(e.getId());}
    @Transactional(readOnly=true) public List<RecoverableReceipt> recoverableReceipts(Long ownerId,Long id){Expense e=require(business(ownerId),id);return recoverableReceiptRepository.findAllByBusinessIdAndBranchIdAndExpenseId(e.getBusinessId(),e.getBranchId(),id);}

    @Transactional(readOnly=true)
    public List<ExpenseResponse> list(Long ownerId, Long branchId, LocalDate from, LocalDate to) {
        Long businessId=business(ownerId); requireBranch(businessId,branchId);
        LocalDate safeFrom=from==null?LocalDate.of(1970,1,1):from, safeTo=to==null?LocalDate.now(clock):to;
        if(safeFrom.isAfter(safeTo)) throw new ExpenseValidationException("From date cannot be after To date");
        return expenseRepository.findAllByBusinessIdAndBranchIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(businessId,branchId,safeFrom,safeTo).stream().map(this::response).toList();
    }

    @Transactional(readOnly=true)
    public ExpenseSummaryResponse summary(Long ownerId, Long branchId, LocalDate from, LocalDate to) {
        List<Expense> values = raw(ownerId,branchId,from,to);
        Map<String,BigDecimal> byCategory=values.stream().filter(e->e.getClassification()==ExpenseClassification.OPERATING_EXPENSE && e.getStatus()==ExpenseStatus.POSTED)
                .collect(Collectors.groupingBy(e->Optional.ofNullable(e.getCategoryNameSnapshot()).orElse("Uncategorized"), TreeMap::new,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)));
        BigDecimal operating=byCategory.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal paid=values.stream().filter(e->e.getClassification()==ExpenseClassification.RECOVERABLE_DEPOSIT_ADVANCE && e.getStatus()==ExpenseStatus.POSTED).map(Expense::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        LocalDate safeFrom=from==null?LocalDate.of(1970,1,1):from;LocalDate safeTo=to==null?LocalDate.now(clock):to;
        BigDecimal received=recoverableReceiptRepository.findAllByBusinessIdAndBranchIdAndReceivedAtBetween(
                business(ownerId),branchId,safeFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),safeTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).stream().map(RecoverableReceipt::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal allPaid=expenseRepository.findAllByBusinessIdAndBranchIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(business(ownerId),branchId,LocalDate.of(1970,1,1),safeTo).stream().filter(e->e.getClassification()==ExpenseClassification.RECOVERABLE_DEPOSIT_ADVANCE&&e.getStatus()==ExpenseStatus.POSTED).map(Expense::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal allReceived=recoverableReceiptRepository.findAllByBusinessIdAndBranchId(business(ownerId),branchId).stream().filter(r->!r.getReceivedAt().isAfter(safeTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())).map(RecoverableReceipt::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new ExpenseSummaryResponse(byCategory,operating,paid,received,allPaid.subtract(allReceived));
    }

    @Transactional(readOnly=true)
    public ExpenseMonthwiseResponse monthwise(Long ownerId, Long branchId, YearMonth month) {
        YearMonth safeMonth = month == null ? YearMonth.now(clock) : month;
        List<Expense> values = raw(ownerId, branchId, safeMonth.atDay(1), safeMonth.atEndOfMonth()).stream()
                .filter(e -> e.getClassification() == ExpenseClassification.OPERATING_EXPENSE)
                .filter(e -> e.getStatus() == ExpenseStatus.POSTED).toList();
        Map<LocalDate, Map<String, BigDecimal>> daily = new TreeMap<>();
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (Expense e : values) {
            String category = Optional.ofNullable(e.getCategoryNameSnapshot()).orElse("Uncategorized");
            daily.computeIfAbsent(e.getExpenseDate(), ignored -> new TreeMap<>()).merge(category, e.getAmount(), BigDecimal::add);
            totals.merge(category, e.getAmount(), BigDecimal::add);
        }
        return new ExpenseMonthwiseResponse(daily, totals, totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Transactional(readOnly=true)
    public Page<ExpenseResponse> search(Long ownerId, Long branchId, ExpenseListFilter filter, Pageable pageable) {
        Long businessId = business(ownerId);
        requireBranch(businessId, branchId);
        ExpenseListFilter safe = filter == null
                ? new ExpenseListFilter(null, null, null, null, null, null, null, null, null)
                : filter;
        if (safe.from() != null && safe.to() != null && safe.from().isAfter(safe.to())) {
            throw new ExpenseValidationException("From date cannot be after To date");
        }
        return searchRepository.searchExpenses(businessId, branchId, safe, pageable).map(this::response);
    }

    @Transactional(readOnly=true)
    public List<ExpenseResponse> filter(Long ownerId, Long branchId, Long expenseId,
                                        ExpenseClassification classification, Long categoryId,
                                        Long paymentMethodId, ExpenseStatus status, Long createdBy,
                                        LocalDate from, LocalDate to) {
        return raw(ownerId, branchId, from, to).stream()
                .filter(e -> expenseId == null || e.getId().equals(expenseId))
                .filter(e -> classification == null || e.getClassification() == classification)
                .filter(e -> categoryId == null || categoryId.equals(e.getCategoryId()))
                .filter(e -> paymentMethodId == null || paymentMethodId.equals(e.getPaymentMethodId()))
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> createdBy == null || createdBy.equals(e.getCreatedBy()))
                .map(this::response).toList();
    }

    private List<Expense> raw(Long ownerId,Long branchId,LocalDate from,LocalDate to){ Long b=business(ownerId); requireBranch(b,branchId); return expenseRepository.findAllByBusinessIdAndBranchIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(b,branchId,from==null?LocalDate.of(1970,1,1):from,to==null?LocalDate.now(clock):to); }
    private ExpenseResponse transition(Long ownerId,Long id,String action,java.util.function.Consumer<Expense> work){ Long b=business(ownerId); Expense e=require(b,id); try{work.accept(e);}catch(IllegalStateException x){throw new ExpenseStateException(x.getMessage());} audit(e,action,actor(ownerId),null,Instant.now(clock)); return response(e); }
    private Context context(Long ownerId, ExpenseRequest r){ if(r==null||r.getBranchId()==null||r.getClassification()==null||r.getPaymentMethodId()==null||r.getExpenseDate()==null||r.getAmount()==null||r.getAmount().signum()<=0||r.getTitle()==null||r.getTitle().isBlank()||r.getPaymentReference()==null||r.getPaymentReference().isBlank()||r.getPaidFromReference()==null||r.getPaidFromReference().isBlank()||r.getIdempotencyKey()==null||r.getIdempotencyKey().isBlank()) throw new ExpenseValidationException("Required Expense information is missing"); Long b=business(ownerId); requireBranch(b,r.getBranchId()); PaymentMethodResponse m=paymentMethodAccessService.findActiveForBranch(b,r.getBranchId(),r.getPaymentMethodId()).orElseThrow(()->new ExpenseValidationException("Payment Method is inactive or unavailable for this branch")); ExpenseCategory c=null; if(r.getClassification()==ExpenseClassification.OPERATING_EXPENSE){ if(r.getCategoryId()==null) throw new ExpenseValidationException("Expense Category is required for Operating Expense"); c=categoryRepository.findByIdAndBusinessIdAndActiveTrueAndArchivedFalse(r.getCategoryId(),b).orElseThrow(()->new ExpenseValidationException("Active Operating Expense Category is required")); } else if(r.getCategoryId()!=null) throw new ExpenseValidationException("Recoverable Deposit/Advance must not use an Expense Category"); if(m.cash()&&(r.getCashLocationId()==null||r.getRegisterId()==null||r.getCashierShiftId()==null)) throw new ExpenseValidationException("Cash expense requires Cash Location, Register and active Cashier Shift"); return new Context(b,m,c); }
    private void requireBranch(Long b,Long branch){ if(branch==null||branchAccessService.findActiveByBusinessIdAndBranchId(b,branch).isEmpty()) throw new ExpenseAccessDeniedException(); }
    private Long business(Long owner){return businessAccessService.findByOwnerId(owner).orElseThrow(ExpenseAccessDeniedException::new).businessId();}
    private Expense require(Long b,Long id){return expenseRepository.findByIdAndBusinessId(id,b).orElseThrow(ExpenseNotFoundException::new);}
    private Long actor(Long ownerId){return currentActorService.actorId(ownerId);}
    private void audit(Expense e,String action,Long actor,String details,Instant now){auditRepository.save(ExpenseAuditEvent.record(e,action,actor,details,now));}
    private ExpenseResponse response(Expense e){return new ExpenseResponse(e.getId(),e.getBranchId(),e.getExpenseDate(),e.getClassification(),e.getCategoryId(),e.getCategoryNameSnapshot(),e.getTitle(),e.getAmount(),e.getPaymentMethodId(),e.getPaymentMethodSnapshot(),e.getPaymentReference(),e.getPaidFromReference(),e.getCashLocationId(),e.getRegisterId(),e.getCashierShiftId(),e.getPayee(),e.getAttachmentReference(),e.getNotes(),e.getStatus(),e.getCashMovementId(),e.getReversalCashMovementId(),e.getReversalReason(),e.getCreatedBy(),e.getApprovedBy(),e.getPostedBy(),e.getCreatedAt(),e.getPostedAt());}
    private BigDecimal money(BigDecimal v){return v.setScale(4,RoundingMode.HALF_UP);}
    private String clean(String v){return v.trim();} private String nullable(String v){return v==null||v.isBlank()?null:v.trim();}
    private record Context(Long businessId, PaymentMethodResponse method, ExpenseCategory category){}
}
