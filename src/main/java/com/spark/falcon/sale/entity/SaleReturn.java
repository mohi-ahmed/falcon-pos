package com.spark.falcon.sale.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.customer.entity.Customer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sale_returns", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sale_return_reference", columnNames = {"business_id", "branch_id", "reference_number"}),
        @UniqueConstraint(name = "uk_sale_return_idempotency", columnNames = {"business_id", "branch_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_sale_return_sale", columnList = "sale_id"),
        @Index(name = "idx_sale_return_customer_time", columnList = "customer_id,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReturn {

    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_return_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "sale_id", nullable = false, updatable = false)
    private Long saleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_return_sale"))
    @Getter(AccessLevel.NONE)
    private Sale saleReference;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_return_customer"))
    @Getter(AccessLevel.NONE)
    private Customer customerReference;

    @Column(name = "reference_number", nullable = false, updatable = false, length = 100)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 30)
    private SaleReturnSettlementType settlementType;

    @Column(name = "total_return_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalReturnAmount;

    @Column(name = "due_reduction_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal dueReductionAmount;

    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundAmount;

    @Column(name = "customer_credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal customerCreditAmount;

    @Column(name = "refund_payment_id")
    private Long refundPaymentId;

    @Column(length = 1000)
    private String notes;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleReturnStatus status;

    @Column(name = "created_by_actor_id", nullable = false, updatable = false)
    private Long createdByActorId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "reversed_by_actor_id")
    private Long reversedByActorId;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static SaleReturn draft(Long businessId,
                                   Long branchId,
                                   Long saleId,
                                   Long customerId,
                                   String referenceNumber,
                                   SaleReturnSettlementType settlementType,
                                   BigDecimal totalReturnAmount,
                                   String idempotencyKey,
                                   String notes,
                                   Long actorId,
                                   Instant now) {
        SaleReturn value = new SaleReturn();
        value.businessId = Objects.requireNonNull(businessId, "businessId is required");
        value.branchId = Objects.requireNonNull(branchId, "branchId is required");
        value.saleId = Objects.requireNonNull(saleId, "saleId is required");
        value.customerId = Objects.requireNonNull(customerId, "customerId is required");
        value.referenceNumber = required(referenceNumber, "referenceNumber");
        value.settlementType = Objects.requireNonNull(settlementType, "settlementType is required");
        value.totalReturnAmount = money(totalReturnAmount);
        value.dueReductionAmount = zero();
        value.refundAmount = zero();
        value.customerCreditAmount = zero();
        value.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        value.notes = optional(notes);
        value.status = SaleReturnStatus.DRAFT;
        value.createdByActorId = Objects.requireNonNull(actorId, "actorId is required");
        value.createdAt = Objects.requireNonNull(now, "now is required");
        value.updatedAt = now;
        return value;
    }

    public void confirm(BigDecimal dueReduction,
                        BigDecimal refundAmount,
                        BigDecimal customerCreditAmount,
                        Long refundPaymentId,
                        Instant now) {
        requireDraft();
        BigDecimal due = money(dueReduction);
        BigDecimal refund = money(refundAmount);
        BigDecimal credit = money(customerCreditAmount);
        if (due.add(refund).add(credit).compareTo(totalReturnAmount) != 0) {
            throw new IllegalArgumentException("Sales Return settlement must equal total return amount");
        }
        this.dueReductionAmount = due;
        this.refundAmount = refund;
        this.customerCreditAmount = credit;
        this.refundPaymentId = refundPaymentId;
        status = SaleReturnStatus.CONFIRMED;
        confirmedAt = Objects.requireNonNull(now, "now is required");
        updatedAt = now;
    }

    public void markReversed(Long actorId, String reason, Instant now) {
        if (status != SaleReturnStatus.CONFIRMED) {
            throw new IllegalStateException("Only a Confirmed Sales Return can be reversed");
        }
        status = SaleReturnStatus.REVERSED;
        reversedByActorId = Objects.requireNonNull(actorId, "actorId is required");
        reversalReason = required(reason, "reason");
        reversedAt = Objects.requireNonNull(now, "now is required");
        updatedAt = now;
    }

    public boolean isDraft() {
        return status == SaleReturnStatus.DRAFT;
    }

    private void requireDraft() {
        if (!isDraft()) throw new IllegalStateException("Only a Draft Sales Return can be confirmed");
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Money value must not be negative");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
