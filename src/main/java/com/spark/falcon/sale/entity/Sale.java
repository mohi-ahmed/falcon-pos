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
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "sales", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sale_idempotency", columnNames = {"business_id", "branch_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_sale_branch_time", columnList = "business_id,branch_id,created_at"),
        @Index(name = "idx_sale_customer_time", columnList = "customer_id,created_at"),
        @Index(name = "idx_sale_branch_due_date", columnList = "business_id,branch_id,due_date"),
        @Index(name = "idx_sale_status", columnList = "business_id,branch_id,status,payment_status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale {

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
            foreignKey = @ForeignKey(name = "fk_sale_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_customer"))
    @Getter(AccessLevel.NONE)
    private Customer customerReference;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SaleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private SalePaymentStatus paymentStatus;

    @Column(name = "gross_item_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossItemTotal;

    @Column(name = "item_discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemDiscountTotal;

    @Column(name = "item_tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemTaxTotal;

    @Column(name = "item_payable_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemPayableTotal;

    @Column(name = "order_discount", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderDiscount;

    @Column(name = "shipping_charge", nullable = false, precision = 19, scale = 4)
    private BigDecimal shippingCharge;

    @Column(name = "other_charge", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherCharge;

    @Column(name = "total_payable", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPayable;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "due_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal dueAmount;

    @Column(name = "change_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal changeAmount;

    @Column(name = "returned_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal returnedAmount;

    @Column(name = "cogs_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal cogsTotal;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_by_actor_id", nullable = false, updatable = false)
    private Long createdByActorId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "corrected_by_actor_id")
    private Long correctedByActorId;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "corrected_at")
    private Instant correctedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Sale draft(Long businessId,
                             Long branchId,
                             Long customerId,
                             String idempotencyKey,
                             BigDecimal grossItemTotal,
                             BigDecimal itemDiscountTotal,
                             BigDecimal itemTaxTotal,
                             BigDecimal itemPayableTotal,
                             BigDecimal orderDiscount,
                             BigDecimal shippingCharge,
                             BigDecimal otherCharge,
                             BigDecimal totalPayable,
                             LocalDate dueDate,
                             String notes,
                             Long actorId,
                             Instant now) {
        Sale sale = new Sale();
        sale.businessId = Objects.requireNonNull(businessId, "businessId is required");
        sale.branchId = Objects.requireNonNull(branchId, "branchId is required");
        sale.customerId = Objects.requireNonNull(customerId, "customerId is required");
        sale.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        sale.status = SaleStatus.DRAFT;
        sale.paymentStatus = SalePaymentStatus.UNPAID;
        sale.grossItemTotal = money(grossItemTotal);
        sale.itemDiscountTotal = money(itemDiscountTotal);
        sale.itemTaxTotal = money(itemTaxTotal);
        sale.itemPayableTotal = money(itemPayableTotal);
        sale.orderDiscount = money(orderDiscount);
        sale.shippingCharge = money(shippingCharge);
        sale.otherCharge = money(otherCharge);
        sale.totalPayable = money(totalPayable);
        sale.paidAmount = zero();
        sale.dueAmount = sale.totalPayable;
        sale.changeAmount = zero();
        sale.returnedAmount = zero();
        sale.cogsTotal = zero();
        sale.dueDate = dueDate;
        sale.notes = optional(notes);
        sale.createdByActorId = Objects.requireNonNull(actorId, "actorId is required");
        sale.createdAt = Objects.requireNonNull(now, "now is required");
        sale.updatedAt = now;
        return sale;
    }

    public void hold(Instant now) {
        requireStatus(SaleStatus.DRAFT, "Only a Draft Sale can be held");
        status = SaleStatus.HELD;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void updateDraftOrHeld(Long customerId,
                                  BigDecimal grossItemTotal,
                                  BigDecimal itemDiscountTotal,
                                  BigDecimal itemTaxTotal,
                                  BigDecimal itemPayableTotal,
                                  BigDecimal orderDiscount,
                                  BigDecimal shippingCharge,
                                  BigDecimal otherCharge,
                                  BigDecimal totalPayable,
                                  LocalDate dueDate,
                                  String notes,
                                  Instant now) {
        if (!isDraftOrHeld()) throw new IllegalStateException("Only a Draft or Held Sale can be edited");
        if (paidAmount.signum() != 0 || returnedAmount.signum() != 0 || cogsTotal.signum() != 0) {
            throw new IllegalStateException("A Sale with posted effects cannot be edited");
        }
        this.customerId = Objects.requireNonNull(customerId, "customerId is required");
        this.grossItemTotal = money(grossItemTotal);
        this.itemDiscountTotal = money(itemDiscountTotal);
        this.itemTaxTotal = money(itemTaxTotal);
        this.itemPayableTotal = money(itemPayableTotal);
        this.orderDiscount = money(orderDiscount);
        this.shippingCharge = money(shippingCharge);
        this.otherCharge = money(otherCharge);
        this.totalPayable = money(totalPayable);
        this.dueDate = dueDate;
        this.dueAmount = this.totalPayable;
        this.notes = optional(notes);
        this.updatedAt = Objects.requireNonNull(now, "now is required");
        recalculatePaymentStatus();
    }

    public void attachCogs(BigDecimal cogsAmount, Instant now) {
        if (status != SaleStatus.DRAFT) {
            throw new IllegalStateException("COGS can be attached only while Sale is Draft");
        }
        BigDecimal amount = money(cogsAmount);
        if (amount.signum() < 0) throw new IllegalArgumentException("COGS must not be negative");
        cogsTotal = cogsTotal.add(amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void confirm(BigDecimal change, Instant now) {
        requireStatus(SaleStatus.DRAFT, "Only a Draft Sale can be confirmed");
        status = SaleStatus.CONFIRMED;
        changeAmount = money(change);
        confirmedAt = Objects.requireNonNull(now, "now is required");
        updatedAt = now;
        recalculatePaymentStatus();
    }

    public PaymentChange applyCustomerPayment(BigDecimal amount, Instant now) {
        requireConfirmedOperational();
        BigDecimal payment = positiveMoney(amount, "Payment amount");
        if (payment.compareTo(dueAmount) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed Sale outstanding due");
        }
        BigDecimal before = dueAmount;
        paidAmount = paidAmount.add(payment).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        recalculateDue();
        updatedAt = Objects.requireNonNull(now, "now is required");
        return new PaymentChange(before, dueAmount);
    }

    public PaymentChange reverseCustomerPayment(BigDecimal amount, Instant now) {
        requireConfirmedOperational();
        BigDecimal payment = positiveMoney(amount, "Payment reversal amount");
        if (payment.compareTo(paidAmount) > 0) {
            throw new IllegalArgumentException("Payment reversal exceeds applied Sale payments");
        }
        BigDecimal before = dueAmount;
        paidAmount = paidAmount.subtract(payment).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        recalculateDue();
        updatedAt = Objects.requireNonNull(now, "now is required");
        return new PaymentChange(before, dueAmount);
    }

    public ReturnChange applyReturn(BigDecimal returnAmount, boolean fullyReturned, Instant now) {
        requireReturnEligible();
        BigDecimal amount = positiveMoney(returnAmount, "Return amount");
        BigDecimal remainingNetValue = totalPayable.subtract(returnedAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (amount.compareTo(remainingNetValue) > 0) {
            throw new IllegalArgumentException("Return amount exceeds remaining Sale value");
        }
        BigDecimal dueBefore = dueAmount;
        returnedAmount = returnedAmount.add(amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        recalculateDue();
        status = fullyReturned ? SaleStatus.RETURNED : SaleStatus.PARTIALLY_RETURNED;
        updatedAt = Objects.requireNonNull(now, "now is required");
        return new ReturnChange(dueBefore, dueAmount, dueBefore.subtract(dueAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    public void reverseReturn(BigDecimal returnAmount, boolean stillHasConfirmedReturns, Instant now) {
        if (status != SaleStatus.PARTIALLY_RETURNED && status != SaleStatus.RETURNED) {
            throw new IllegalStateException("Sale has no Confirmed Return to reverse");
        }
        BigDecimal amount = positiveMoney(returnAmount, "Return reversal amount");
        if (amount.compareTo(returnedAmount) > 0) {
            throw new IllegalArgumentException("Return reversal exceeds Sale returned amount");
        }
        returnedAmount = returnedAmount.subtract(amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        recalculateDue();
        status = stillHasConfirmedReturns ? SaleStatus.PARTIALLY_RETURNED : SaleStatus.CONFIRMED;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void voidSale(Long actorId, String reason, Instant now) {
        requireCorrectionEligible();
        status = SaleStatus.VOIDED;
        recordCorrection(actorId, reason, now);
    }

    public void reverseSale(Long actorId, String reason, Instant now) {
        requireCorrectionEligible();
        status = SaleStatus.REVERSED;
        recordCorrection(actorId, reason, now);
    }

    public boolean isDuePaymentEligible() {
        return (status == SaleStatus.CONFIRMED || status == SaleStatus.PARTIALLY_RETURNED)
                && dueAmount.signum() > 0;
    }

    public boolean isReturnEligible() {
        return status == SaleStatus.CONFIRMED || status == SaleStatus.PARTIALLY_RETURNED;
    }

    public boolean isDraftOrHeld() {
        return status == SaleStatus.DRAFT || status == SaleStatus.HELD;
    }

    public BigDecimal netPayableAfterReturns() {
        return totalPayable.subtract(returnedAmount).max(zero()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void requireReturnEligible() {
        if (!isReturnEligible()) {
            throw new IllegalStateException("Sale is not eligible for a Sales Return");
        }
    }

    private void requireCorrectionEligible() {
        if (status != SaleStatus.CONFIRMED) {
            throw new IllegalStateException("Only a Confirmed Sale without returns can be voided or reversed");
        }
        if (returnedAmount.signum() != 0) {
            throw new IllegalStateException("Reverse existing Sales Returns before correcting the original Sale");
        }
    }

    private void requireConfirmedOperational() {
        if (status != SaleStatus.CONFIRMED && status != SaleStatus.PARTIALLY_RETURNED) {
            throw new IllegalStateException("Sale is not eligible for this operation");
        }
    }

    private void requireStatus(SaleStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    private void recordCorrection(Long actorId, String reason, Instant now) {
        correctedByActorId = Objects.requireNonNull(actorId, "actorId is required");
        correctionReason = required(reason, "reason");
        correctedAt = Objects.requireNonNull(now, "now is required");
        updatedAt = now;
    }

    private void recalculateDue() {
        BigDecimal netPayable = netPayableAfterReturns();
        dueAmount = netPayable.subtract(paidAmount).max(zero()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        recalculatePaymentStatus();
    }

    private void recalculatePaymentStatus() {
        if (dueAmount.signum() == 0) {
            paymentStatus = SalePaymentStatus.PAID;
        } else if (paidAmount.signum() > 0) {
            paymentStatus = SalePaymentStatus.PARTIALLY_PAID;
        } else {
            paymentStatus = SalePaymentStatus.UNPAID;
        }
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Money value must not be negative");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveMoney(BigDecimal value, String field) {
        BigDecimal normalized = money(value);
        if (normalized.signum() <= 0) throw new IllegalArgumentException(field + " must be greater than zero");
        return normalized;
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

    public record PaymentChange(BigDecimal dueBefore, BigDecimal dueAfter) {}
    public record ReturnChange(BigDecimal dueBefore, BigDecimal dueAfter, BigDecimal dueReduction) {}
}
