package com.spark.falcon.purchase.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnSettlementType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus;
import com.spark.falcon.settings.entity.PaymentMethod;
import com.spark.falcon.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "purchase_returns", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_return_reference", columnNames = {"business_id", "branch_id", "reference_number"}),
        @UniqueConstraint(name = "uk_purchase_return_idempotency", columnNames = {"business_id", "branch_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_purchase_return_purchase", columnList = "purchase_id"),
        @Index(name = "idx_purchase_return_supplier_date", columnList = "supplier_id,return_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_return_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "purchase_id", nullable = false, updatable = false)
    private Long purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_return_purchase"))
    @Getter(AccessLevel.NONE)
    private Purchase purchaseReference;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_return_supplier"))
    @Getter(AccessLevel.NONE)
    private Supplier supplierReference;

    @Column(name = "reference_number", nullable = false, length = 100)
    private String referenceNumber;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 40)
    private PurchaseReturnSettlementType settlementType;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_return_payment_method"))
    @Getter(AccessLevel.NONE)
    private PaymentMethod paymentMethodReference;

    @Column(name = "transaction_reference", length = 160)
    private String transactionReference;

    @Column(name = "total_return_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalReturnAmount;

    @Column(name = "supplier_due_reduction", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplierDueReduction;

    @Column(name = "supplier_credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal supplierCreditAmount;

    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundAmount;

    @Column(name = "cash_movement_id")
    private Long cashMovementId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseReturnStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_actor_type", nullable = false, updatable = false, length = 20)
    private PurchaseActorType createdByActorType;

    @Column(name = "created_by_actor_id", nullable = false, updatable = false)
    private Long createdByActorId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static PurchaseReturn draft(Long businessId, Long branchId, Long purchaseId, Long supplierId,
                                       String referenceNumber, LocalDate returnDate, String notes,
                                       PurchaseReturnSettlementType settlementType, Long paymentMethodId,
                                       String transactionReference, BigDecimal totalReturnAmount,
                                       String idempotencyKey, PurchaseActorType actorType, Long actorId, Instant now) {
        PurchaseReturn value = new PurchaseReturn();
        value.businessId = Objects.requireNonNull(businessId, "businessId is required");
        value.branchId = Objects.requireNonNull(branchId, "branchId is required");
        value.purchaseId = Objects.requireNonNull(purchaseId, "purchaseId is required");
        value.supplierId = Objects.requireNonNull(supplierId, "supplierId is required");
        value.referenceNumber = required(referenceNumber, "referenceNumber");
        value.returnDate = Objects.requireNonNull(returnDate, "returnDate is required");
        value.notes = optional(notes);
        value.settlementType = Objects.requireNonNull(settlementType, "settlementType is required");
        value.paymentMethodId = paymentMethodId;
        value.transactionReference = optional(transactionReference);
        value.totalReturnAmount = Objects.requireNonNull(totalReturnAmount, "totalReturnAmount is required");
        value.supplierDueReduction = BigDecimal.ZERO.setScale(4);
        value.supplierCreditAmount = BigDecimal.ZERO.setScale(4);
        value.refundAmount = BigDecimal.ZERO.setScale(4);
        value.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        value.status = PurchaseReturnStatus.DRAFT;
        value.createdByActorType = Objects.requireNonNull(actorType, "actorType is required");
        value.createdByActorId = Objects.requireNonNull(actorId, "actorId is required");
        value.createdAt = Objects.requireNonNull(now, "now is required");
        value.updatedAt = now;
        return value;
    }

    public void reviseDraft(String referenceNumber, LocalDate returnDate, String notes,
                            PurchaseReturnSettlementType settlementType, Long paymentMethodId,
                            String transactionReference, BigDecimal totalReturnAmount, Instant now) {
        requireDraft();
        this.referenceNumber = required(referenceNumber, "referenceNumber");
        this.returnDate = Objects.requireNonNull(returnDate, "returnDate is required");
        this.notes = optional(notes);
        this.settlementType = Objects.requireNonNull(settlementType, "settlementType is required");
        this.paymentMethodId = paymentMethodId;
        this.transactionReference = optional(transactionReference);
        this.totalReturnAmount = Objects.requireNonNull(totalReturnAmount, "totalReturnAmount is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void confirm(BigDecimal dueReduction, BigDecimal supplierCredit, BigDecimal refund,
                        Long cashMovementId, Instant now) {
        requireDraft();
        this.supplierDueReduction = Objects.requireNonNull(dueReduction, "dueReduction is required");
        this.supplierCreditAmount = Objects.requireNonNull(supplierCredit, "supplierCredit is required");
        this.refundAmount = Objects.requireNonNull(refund, "refund is required");
        this.cashMovementId = cashMovementId;
        this.status = PurchaseReturnStatus.CONFIRMED;
        this.confirmedAt = Objects.requireNonNull(now, "now is required");
        this.updatedAt = now;
    }

    public boolean isDraft() {
        return status == PurchaseReturnStatus.DRAFT;
    }

    private void requireDraft() {
        if (!isDraft()) throw new IllegalStateException("Only Draft purchase return can be edited or confirmed");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
