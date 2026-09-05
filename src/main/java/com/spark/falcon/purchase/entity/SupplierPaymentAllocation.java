package com.spark.falcon.purchase.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "supplier_payment_allocations", indexes = {
        @Index(name = "idx_supplier_payment_allocation_payment", columnList = "supplier_payment_id"),
        @Index(name = "idx_supplier_payment_allocation_purchase", columnList = "purchase_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierPaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_payment_id", nullable = false, updatable = false)
    private Long supplierPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_payment_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_payment_allocation_payment"))
    @Getter(AccessLevel.NONE)
    private SupplierPayment supplierPaymentReference;

    @Column(name = "purchase_id", nullable = false, updatable = false)
    private Long purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_payment_allocation_purchase"))
    @Getter(AccessLevel.NONE)
    private Purchase purchaseReference;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "due_before", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal dueBefore;

    @Column(name = "due_after", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal dueAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SupplierPaymentAllocation create(Long supplierPaymentId, Long purchaseId, BigDecimal amount,
                                                   BigDecimal dueBefore, BigDecimal dueAfter, Instant now) {
        SupplierPaymentAllocation allocation = new SupplierPaymentAllocation();
        allocation.supplierPaymentId = Objects.requireNonNull(supplierPaymentId, "supplierPaymentId is required");
        allocation.purchaseId = Objects.requireNonNull(purchaseId, "purchaseId is required");
        allocation.amount = Objects.requireNonNull(amount, "amount is required");
        allocation.dueBefore = Objects.requireNonNull(dueBefore, "dueBefore is required");
        allocation.dueAfter = Objects.requireNonNull(dueAfter, "dueAfter is required");
        allocation.createdAt = Objects.requireNonNull(now, "now is required");
        return allocation;
    }
}
