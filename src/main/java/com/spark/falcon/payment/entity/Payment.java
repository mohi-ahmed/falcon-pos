package com.spark.falcon.payment.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.cashmanagement.entity.CashMovement;
import com.spark.falcon.customer.entity.Customer;
import com.spark.falcon.settings.entity.PaymentMethod;
import com.spark.falcon.supplier.entity.Supplier;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_idempotency", columnNames = {"business_id", "branch_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_reversal_of", columnNames = {"reversal_of_payment_id"})
}, indexes = {
        @Index(name = "idx_payment_branch_time", columnList = "business_id,branch_id,created_at"),
        @Index(name = "idx_payment_supplier_time", columnList = "supplier_id,created_at"),
        @Index(name = "idx_payment_customer_time", columnList = "customer_id,created_at"),
        @Index(name = "idx_payment_method_time", columnList = "payment_method_id,created_at"),
        @Index(name = "idx_payment_status_time", columnList = "status,created_at")
})
@lombok.Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

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
            foreignKey = @ForeignKey(name = "fk_payment_branch"))
    @lombok.Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, updatable = false, length = 20)
    private PaymentPartyType partyType;

    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_customer"))
    @lombok.Getter(AccessLevel.NONE)
    private Customer customerReference;

    @Column(name = "supplier_id", updatable = false)
    private Long supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_supplier"))
    @lombok.Getter(AccessLevel.NONE)
    private Supplier supplierReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private PaymentDirection direction;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "allocated_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_settlement_type", updatable = false, length = 30)
    private CustomerPaymentSettlementType customerSettlementType;

    @Column(name = "customer_credit_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal customerCreditAmount;

    @Column(name = "change_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal changeAmount;

    @Column(nullable = false, updatable = false, length = 10)
    private String currency;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private Long paymentMethodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_method"))
    @lombok.Getter(AccessLevel.NONE)
    private PaymentMethod paymentMethodReference;

    @Column(name = "payment_method_name_snapshot", nullable = false, updatable = false, length = 120)
    private String paymentMethodNameSnapshot;

    @Column(name = "payment_method_code_snapshot", nullable = false, updatable = false, length = 60)
    private String paymentMethodCodeSnapshot;

    @Column(name = "cash_payment", nullable = false, updatable = false)
    private boolean cashPayment;

    @Column(name = "transaction_reference", length = 160, updatable = false)
    private String transactionReference;

    @Column(name = "account_reference", length = 160, updatable = false)
    private String accountReference;

    @Column(name = "cash_location_id", updatable = false)
    private Long cashLocationId;

    @Column(name = "register_id", updatable = false)
    private Long registerId;

    @Column(name = "cashier_shift_id", updatable = false)
    private Long cashierShiftId;

    @Column(name = "cash_movement_id")
    private Long cashMovementId;

    @Column(name = "source_transaction_id", updatable = false)
    private Long sourceTransactionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_movement_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_cash_movement"))
    @lombok.Getter(AccessLevel.NONE)
    private CashMovement cashMovementReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_module", nullable = false, updatable = false, length = 20)
    private PaymentSourceModule sourceModule;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_purpose", nullable = false, updatable = false, length = 50)
    private PaymentFinancialPurpose financialPurpose;

    @Column(name = "created_by_actor_id", nullable = false, updatable = false)
    private Long createdByActorId;

    @Column(name = "confirmed_by_actor_id", nullable = false, updatable = false)
    private Long confirmedByActorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "reversal_of_payment_id", updatable = false)
    private Long reversalOfPaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_payment_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_reversal_original"))
    @lombok.Getter(AccessLevel.NONE)
    private Payment reversalOfReference;

    @Column(name = "reversed_by_payment_id")
    private Long reversedByPaymentId;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason", length = 500, updatable = false)
    private String reversalReason;

    @Column(name = "attachment_reference", length = 500, updatable = false)
    private String attachmentReference;

    @Column(length = 1000, updatable = false)
    private String notes;

    @Version
    @Column(nullable = false)
    private long version;

    public static Payment confirmedSupplierPayment(Long businessId,
                                                   Long branchId,
                                                   Long supplierId,
                                                   PaymentDirection direction,
                                                   BigDecimal amount,
                                                   String currency,
                                                   Long paymentMethodId,
                                                   String methodName,
                                                   String methodCode,
                                                   boolean cashPayment,
                                                   String transactionReference,
                                                   String accountReference,
                                                   Long cashLocationId,
                                                   Long registerId,
                                                   Long cashierShiftId,
                                                   PaymentSourceModule sourceModule,
                                                   PaymentFinancialPurpose financialPurpose,
                                                   Long actorId,
                                                   String idempotencyKey,
                                                   String attachmentReference,
                                                   String notes,
                                                   Long reversalOfPaymentId,
                                                   String reversalReason,
                                                   Instant now) {
        Payment payment = new Payment();
        payment.businessId = Objects.requireNonNull(businessId, "businessId is required");
        payment.branchId = Objects.requireNonNull(branchId, "branchId is required");
        payment.partyType = PaymentPartyType.SUPPLIER;
        payment.supplierId = Objects.requireNonNull(supplierId, "supplierId is required");
        payment.direction = Objects.requireNonNull(direction, "direction is required");
        payment.amount = money(amount);
        payment.currency = required(currency, "currency");
        payment.paymentMethodId = Objects.requireNonNull(paymentMethodId, "paymentMethodId is required");
        payment.paymentMethodNameSnapshot = required(methodName, "methodName");
        payment.paymentMethodCodeSnapshot = required(methodCode, "methodCode");
        payment.cashPayment = cashPayment;
        payment.transactionReference = optional(transactionReference);
        payment.accountReference = optional(accountReference);
        payment.cashLocationId = cashLocationId;
        payment.registerId = registerId;
        payment.cashierShiftId = cashierShiftId;
        payment.status = PaymentStatus.CONFIRMED;
        payment.sourceModule = Objects.requireNonNull(sourceModule, "sourceModule is required");
        payment.financialPurpose = Objects.requireNonNull(financialPurpose, "financialPurpose is required");
        payment.createdByActorId = Objects.requireNonNull(actorId, "actorId is required");
        payment.confirmedByActorId = actorId;
        payment.createdAt = Objects.requireNonNull(now, "now is required");
        payment.confirmedAt = now;
        payment.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        payment.attachmentReference = optional(attachmentReference);
        payment.notes = optional(notes);
        payment.reversalOfPaymentId = reversalOfPaymentId;
        payment.reversalReason = optional(reversalReason);
        return payment;
    }

    public static Payment confirmedCustomerPayment(Long businessId,
                                                   Long branchId,
                                                   Long customerId,
                                                   PaymentDirection direction,
                                                   BigDecimal amount,
                                                   String currency,
                                                   Long paymentMethodId,
                                                   String methodName,
                                                   String methodCode,
                                                   boolean cashPayment,
                                                   String transactionReference,
                                                   String accountReference,
                                                   Long cashLocationId,
                                                   Long registerId,
                                                   Long cashierShiftId,
                                                   PaymentSourceModule sourceModule,
                                                   PaymentFinancialPurpose financialPurpose,
                                                   Long sourceTransactionId,
                                                   Long actorId,
                                                   String idempotencyKey,
                                                   String attachmentReference,
                                                   String notes,
                                                   Long reversalOfPaymentId,
                                                   String reversalReason,
                                                   Instant now) {
        return confirmedCustomerPayment(businessId, branchId, customerId, direction, amount, currency,
                paymentMethodId, methodName, methodCode, cashPayment, transactionReference, accountReference,
                cashLocationId, registerId, cashierShiftId, sourceModule, financialPurpose, sourceTransactionId,
                actorId, idempotencyKey, attachmentReference, notes, reversalOfPaymentId, reversalReason, now, now);
    }

    public static Payment confirmedCustomerPayment(Long businessId,
                                                   Long branchId,
                                                   Long customerId,
                                                   PaymentDirection direction,
                                                   BigDecimal amount,
                                                   String currency,
                                                   Long paymentMethodId,
                                                   String methodName,
                                                   String methodCode,
                                                   boolean cashPayment,
                                                   String transactionReference,
                                                   String accountReference,
                                                   Long cashLocationId,
                                                   Long registerId,
                                                   Long cashierShiftId,
                                                   PaymentSourceModule sourceModule,
                                                   PaymentFinancialPurpose financialPurpose,
                                                   Long sourceTransactionId,
                                                   Long actorId,
                                                   String idempotencyKey,
                                                   String attachmentReference,
                                                   String notes,
                                                   Long reversalOfPaymentId,
                                                   String reversalReason,
                                                   Instant paymentAt,
                                                   Instant now) {
        Payment payment = new Payment();
        payment.businessId = Objects.requireNonNull(businessId, "businessId is required");
        payment.branchId = Objects.requireNonNull(branchId, "branchId is required");
        payment.partyType = PaymentPartyType.CUSTOMER;
        payment.customerId = Objects.requireNonNull(customerId, "customerId is required");
        payment.direction = Objects.requireNonNull(direction, "direction is required");
        payment.amount = money(amount);
        payment.currency = required(currency, "currency");
        payment.paymentMethodId = Objects.requireNonNull(paymentMethodId, "paymentMethodId is required");
        payment.paymentMethodNameSnapshot = required(methodName, "methodName");
        payment.paymentMethodCodeSnapshot = required(methodCode, "methodCode");
        payment.cashPayment = cashPayment;
        payment.transactionReference = optional(transactionReference);
        payment.accountReference = optional(accountReference);
        payment.cashLocationId = cashLocationId;
        payment.registerId = registerId;
        payment.cashierShiftId = cashierShiftId;
        payment.status = PaymentStatus.CONFIRMED;
        payment.sourceModule = Objects.requireNonNull(sourceModule, "sourceModule is required");
        payment.financialPurpose = Objects.requireNonNull(financialPurpose, "financialPurpose is required");
        payment.sourceTransactionId = Objects.requireNonNull(sourceTransactionId, "sourceTransactionId is required");
        payment.createdByActorId = Objects.requireNonNull(actorId, "actorId is required");
        payment.confirmedByActorId = actorId;
        payment.createdAt = Objects.requireNonNull(paymentAt, "paymentAt is required");
        payment.confirmedAt = Objects.requireNonNull(now, "now is required");
        payment.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        payment.attachmentReference = optional(attachmentReference);
        payment.notes = optional(notes);
        payment.reversalOfPaymentId = reversalOfPaymentId;
        payment.reversalReason = optional(reversalReason);
        return payment;
    }

    public void attachCashMovement(Long cashMovementId) {
        if (!cashPayment) {
            throw new IllegalStateException("Non-cash Payment cannot have a Cash Movement");
        }
        if (this.cashMovementId != null) {
            throw new IllegalStateException("Cash Movement is already attached");
        }
        this.cashMovementId = Objects.requireNonNull(cashMovementId, "cashMovementId is required");
    }

    public void recordCustomerSettlement(BigDecimal allocatedAmount,
                                         CustomerPaymentSettlementType settlementType,
                                         BigDecimal customerCreditAmount,
                                         BigDecimal changeAmount) {
        if (partyType != PaymentPartyType.CUSTOMER || status != PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("Only a Confirmed Customer Payment can record settlement");
        }
        if (this.allocatedAmount != null) {
            throw new IllegalStateException("Customer Payment settlement is already recorded");
        }
        BigDecimal allocated = nonNegativeMoney(allocatedAmount, "allocatedAmount");
        BigDecimal credit = nonNegativeMoney(customerCreditAmount, "customerCreditAmount");
        BigDecimal change = nonNegativeMoney(changeAmount, "changeAmount");
        CustomerPaymentSettlementType type = Objects.requireNonNull(settlementType, "settlementType is required");
        if (credit.signum() > 0 && change.signum() > 0) {
            throw new IllegalArgumentException("Customer Credit and Change cannot both be recorded");
        }
        if (type == CustomerPaymentSettlementType.CUSTOMER_CREDIT && credit.signum() <= 0) {
            throw new IllegalArgumentException("Customer Credit settlement requires a positive credit amount");
        }
        if (type == CustomerPaymentSettlementType.CHANGE && change.signum() <= 0) {
            throw new IllegalArgumentException("Change settlement requires a positive change amount");
        }
        if (type == CustomerPaymentSettlementType.ALLOCATED_ONLY && (credit.signum() > 0 || change.signum() > 0)) {
            throw new IllegalArgumentException("Allocated-only settlement cannot contain Customer Credit or Change");
        }
        if (allocated.add(credit).add(change).compareTo(amount) != 0) {
            throw new IllegalArgumentException("Allocation, Customer Credit and Change must equal Amount Received");
        }
        this.allocatedAmount = allocated;
        this.customerSettlementType = type;
        this.customerCreditAmount = credit;
        this.changeAmount = change;
    }

    public BigDecimal effectiveAllocatedAmount() {
        return allocatedAmount == null ? amount : allocatedAmount;
    }

    public BigDecimal effectiveCustomerCreditAmount() {
        return customerCreditAmount == null ? zero() : customerCreditAmount;
    }

    public BigDecimal effectiveChangeAmount() {
        return changeAmount == null ? zero() : changeAmount;
    }

    public void markReversed(Long reversalPaymentId, Instant now) {
        if (status != PaymentStatus.CONFIRMED || reversalOfPaymentId != null) {
            throw new IllegalStateException("Only an original Confirmed Payment can be reversed");
        }
        if (reversedByPaymentId != null) {
            throw new IllegalStateException("Payment has already been reversed");
        }
        status = PaymentStatus.REVERSED;
        reversedByPaymentId = Objects.requireNonNull(reversalPaymentId, "reversalPaymentId is required");
        reversedAt = Objects.requireNonNull(now, "now is required");
    }

    public boolean isOriginalConfirmedPayment() {
        return status == PaymentStatus.CONFIRMED && reversalOfPaymentId == null && reversedByPaymentId == null;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
