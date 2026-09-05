package com.spark.falcon.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_allocations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_allocation_invoice", columnNames = {"payment_id", "invoice_type", "invoice_id"})
}, indexes = {
        @Index(name = "idx_payment_allocation_payment", columnList = "payment_id"),
        @Index(name = "idx_payment_allocation_invoice", columnList = "invoice_type,invoice_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation {

    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_allocation_payment"))
    @Getter(AccessLevel.NONE)
    private Payment paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, updatable = false, length = 20)
    private PaymentInvoiceType invoiceType;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentAllocationEffect effect;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "due_before", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal dueBefore;

    @Column(name = "due_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal dueAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PaymentAllocation create(Long paymentId,
                                           PaymentInvoiceType invoiceType,
                                           Long invoiceId,
                                           PaymentAllocationEffect effect,
                                           BigDecimal amount,
                                           BigDecimal dueBefore,
                                           BigDecimal dueAfter,
                                           Instant now) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.paymentId = Objects.requireNonNull(paymentId, "paymentId is required");
        allocation.invoiceType = Objects.requireNonNull(invoiceType, "invoiceType is required");
        allocation.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId is required");
        allocation.effect = Objects.requireNonNull(effect, "effect is required");
        allocation.amount = money(amount);
        allocation.dueBefore = moneyOrZero(dueBefore);
        allocation.dueAfter = moneyOrZero(dueAfter);
        allocation.createdAt = Objects.requireNonNull(now, "now is required");
        return allocation;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("due amount must not be negative");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
