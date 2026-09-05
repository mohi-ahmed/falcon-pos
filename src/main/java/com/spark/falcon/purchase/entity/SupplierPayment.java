package com.spark.falcon.purchase.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.SupplierPaymentStatus;
import com.spark.falcon.settings.entity.PaymentMethod;
import com.spark.falcon.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "supplier_payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_payment_idempotency", columnNames = {"business_id", "branch_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_supplier_payment_supplier_time", columnList = "supplier_id,created_at"),
        @Index(name = "idx_supplier_payment_branch_time", columnList = "business_id,branch_id,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_payment_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_payment_supplier"))
    @Getter(AccessLevel.NONE)
    private Supplier supplierReference;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private Long paymentMethodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_payment_method"))
    @Getter(AccessLevel.NONE)
    private PaymentMethod paymentMethodReference;

    @Column(name = "payment_method_name_snapshot", nullable = false, updatable = false, length = 120)
    private String paymentMethodNameSnapshot;

    @Column(name = "payment_method_code_snapshot", nullable = false, updatable = false, length = 60)
    private String paymentMethodCodeSnapshot;

    @Column(name = "cash_payment", nullable = false, updatable = false)
    private boolean cashPayment;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_reference", length = 160, updatable = false)
    private String transactionReference;

    @Column(name = "cash_movement_id")
    private Long cashMovementId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierPaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_actor_type", nullable = false, updatable = false, length = 20)
    private PurchaseActorType createdByActorType;

    @Column(name = "created_by_actor_id", nullable = false, updatable = false)
    private Long createdByActorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SupplierPayment confirmed(Long businessId, Long branchId, Long supplierId,
                                            Long paymentMethodId, String methodName, String methodCode,
                                            boolean cashPayment, BigDecimal amount, String transactionReference,
                                            String idempotencyKey, PurchaseActorType actorType, Long actorId,
                                            Instant now) {
        SupplierPayment payment = new SupplierPayment();
        payment.businessId = Objects.requireNonNull(businessId, "businessId is required");
        payment.branchId = Objects.requireNonNull(branchId, "branchId is required");
        payment.supplierId = Objects.requireNonNull(supplierId, "supplierId is required");
        payment.paymentMethodId = Objects.requireNonNull(paymentMethodId, "paymentMethodId is required");
        payment.paymentMethodNameSnapshot = required(methodName, "methodName");
        payment.paymentMethodCodeSnapshot = required(methodCode, "methodCode");
        payment.cashPayment = cashPayment;
        payment.amount = Objects.requireNonNull(amount, "amount is required");
        payment.transactionReference = optional(transactionReference);
        payment.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        payment.status = SupplierPaymentStatus.CONFIRMED;
        payment.createdByActorType = Objects.requireNonNull(actorType, "actorType is required");
        payment.createdByActorId = Objects.requireNonNull(actorId, "actorId is required");
        payment.createdAt = Objects.requireNonNull(now, "now is required");
        return payment;
    }

    public void attachCashMovement(Long cashMovementId) {
        if (!cashPayment) throw new IllegalStateException("Non-cash supplier payment cannot have a Cash Movement");
        if (this.cashMovementId != null) throw new IllegalStateException("Cash Movement is already attached");
        this.cashMovementId = Objects.requireNonNull(cashMovementId, "cashMovementId is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
