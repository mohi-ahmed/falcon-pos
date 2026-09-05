package com.spark.falcon.expense.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expenses", uniqueConstraints = @UniqueConstraint(
        name = "uk_expense_business_branch_key", columnNames = {"business_id", "branch_id", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class Expense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "business_id", nullable = false) private Long businessId;
    @Column(name = "branch_id", nullable = false, updatable = false) private Long branchId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_expense_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;
    @Column(name = "expense_date", nullable = false) private LocalDate expenseDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ExpenseClassification classification;
    @Column(name = "category_id") private Long categoryId;
    @Column(name = "category_name_snapshot", length = 120) private String categoryNameSnapshot;
    @Column(nullable = false, length = 180) private String title;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(name = "payment_method_id", nullable = false) private Long paymentMethodId;
    @Column(name = "payment_method_snapshot", nullable = false, length = 120) private String paymentMethodSnapshot;
    @Column(name = "payment_reference", nullable = false, length = 160) private String paymentReference;
    @Column(name = "paid_from_reference", nullable = false, length = 160) private String paidFromReference;
    @Column(name = "cash_location_id") private Long cashLocationId;
    @Column(name = "register_id") private Long registerId;
    @Column(name = "cashier_shift_id") private Long cashierShiftId;
    @Column(length = 160) private String payee;
    @Column(name = "attachment_reference", length = 500) private String attachmentReference;
    @Column(length = 1000) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ExpenseStatus status;
    @Column(name = "cash_movement_id", unique = true) private Long cashMovementId;
    @Column(name = "reversal_cash_movement_id", unique = true) private Long reversalCashMovementId;
    @Column(name = "reversal_reason", length = 500) private String reversalReason;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "approved_by") private Long approvedBy;
    @Column(name = "posted_by") private Long postedBy;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "posted_at") private Instant postedAt;

    public static Expense draft(Long businessId, Long branchId, LocalDate date, ExpenseClassification classification,
                                Long categoryId, String categorySnapshot, String title, BigDecimal amount,
                                Long paymentMethodId, String methodSnapshot, String paymentReference,
                                String paidFromReference, Long cashLocationId, Long registerId, Long shiftId,
                                String payee, String attachment, String notes, Long actorId, String key, Instant now) {
        Expense e = new Expense(); e.businessId = businessId; e.branchId = branchId; e.createdBy = actorId;
        e.idempotencyKey = key; e.status = ExpenseStatus.DRAFT; e.createdAt = now;
        e.changeDraft(date, classification, categoryId, categorySnapshot, title, amount, paymentMethodId,
                methodSnapshot, paymentReference, paidFromReference, cashLocationId, registerId, shiftId,
                payee, attachment, notes, now); return e;
    }

    public void changeDraft(LocalDate date, ExpenseClassification classification, Long categoryId,
                            String categorySnapshot, String title, BigDecimal amount, Long paymentMethodId,
                            String methodSnapshot, String paymentReference, String paidFromReference,
                            Long cashLocationId, Long registerId, Long shiftId, String payee,
                            String attachment, String notes, Instant now) {
        require(ExpenseStatus.DRAFT); expenseDate = date; this.classification = classification;
        this.categoryId = categoryId; categoryNameSnapshot = categorySnapshot; this.title = title; this.amount = amount;
        this.paymentMethodId = paymentMethodId; paymentMethodSnapshot = methodSnapshot;
        this.paymentReference = paymentReference; this.paidFromReference = paidFromReference;
        this.cashLocationId = cashLocationId; this.registerId = registerId; cashierShiftId = shiftId;
        this.payee = payee; attachmentReference = attachment; this.notes = notes; updatedAt = now;
    }
    public void submit(Instant now) { require(ExpenseStatus.DRAFT); status = ExpenseStatus.SUBMITTED; updatedAt = now; }
    public void approve(Long actor, Instant now) { require(ExpenseStatus.SUBMITTED); approvedBy = actor; status = ExpenseStatus.APPROVED; updatedAt = now; }
    public void reject(Instant now) { require(ExpenseStatus.SUBMITTED); status = ExpenseStatus.REJECTED; updatedAt = now; }
    public void cancel(Instant now) { if (status != ExpenseStatus.SUBMITTED && status != ExpenseStatus.APPROVED) throw new IllegalStateException("Only Submitted or Approved Expense can be cancelled"); status = ExpenseStatus.CANCELLED; updatedAt = now; }
    public void post(Long actor, Long movementId, Instant now) { require(ExpenseStatus.APPROVED); postedBy = actor; cashMovementId = movementId; status = ExpenseStatus.POSTED; postedAt = now; updatedAt = now; }
    public void reverse(Long reversalMovementId, String reason, Instant now) { require(ExpenseStatus.POSTED); reversalCashMovementId = reversalMovementId; reversalReason = reason; status = ExpenseStatus.REVERSED; updatedAt = now; }
    private void require(ExpenseStatus expected) { if (status != expected) throw new IllegalStateException("Expense must be " + expected); }
}
