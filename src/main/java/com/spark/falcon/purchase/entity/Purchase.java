package com.spark.falcon.purchase.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus;
import com.spark.falcon.purchase.entity.enumtype.PurchaseStatus;
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
@Table(name = "purchases", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_idempotency", columnNames = {"business_id", "branch_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_purchase_branch_date", columnList = "business_id,branch_id,purchase_date"),
        @Index(name = "idx_purchase_supplier_date", columnList = "supplier_id,purchase_date"),
        @Index(name = "idx_purchase_status", columnList = "business_id,branch_id,status,payment_status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_supplier"))
    @Getter(AccessLevel.NONE)
    private Supplier supplierReference;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "supplier_invoice_reference", length = 120)
    private String supplierInvoiceReference;

    @Column(length = 1000)
    private String notes;

    @Column(name = "attachment_reference", length = 500)
    private String attachmentReference;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PurchasePaymentStatus paymentStatus;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "item_tax_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemTaxTotal;

    @Column(name = "item_discount_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemDiscountTotal;

    @Column(name = "order_tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderTax;

    @Column(name = "shipping_charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal shippingCharges;

    @Column(name = "other_charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherCharges;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discount;

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

    public static Purchase draft(Long businessId, Long branchId, Long supplierId, LocalDate purchaseDate,
                                 String supplierInvoiceReference, String notes, String attachmentReference,
                                 String idempotencyKey, BigDecimal subtotal, BigDecimal itemTaxTotal,
                                 BigDecimal itemDiscountTotal, BigDecimal orderTax, BigDecimal shippingCharges,
                                 BigDecimal otherCharges, BigDecimal discount, BigDecimal totalPayable,
                                 PurchaseActorType actorType, Long actorId, Instant now) {
        Purchase purchase = new Purchase();
        purchase.businessId = Objects.requireNonNull(businessId, "businessId is required");
        purchase.branchId = Objects.requireNonNull(branchId, "branchId is required");
        purchase.supplierId = Objects.requireNonNull(supplierId, "supplierId is required");
        purchase.purchaseDate = Objects.requireNonNull(purchaseDate, "purchaseDate is required");
        purchase.supplierInvoiceReference = optional(supplierInvoiceReference);
        purchase.notes = optional(notes);
        purchase.attachmentReference = optional(attachmentReference);
        purchase.idempotencyKey = required(idempotencyKey, "idempotencyKey");
        purchase.status = PurchaseStatus.DRAFT;
        purchase.paymentStatus = PurchasePaymentStatus.UNPAID;
        purchase.subtotal = amount(subtotal);
        purchase.itemTaxTotal = amount(itemTaxTotal);
        purchase.itemDiscountTotal = amount(itemDiscountTotal);
        purchase.orderTax = amount(orderTax);
        purchase.shippingCharges = amount(shippingCharges);
        purchase.otherCharges = amount(otherCharges);
        purchase.discount = amount(discount);
        purchase.totalPayable = amount(totalPayable);
        purchase.paidAmount = BigDecimal.ZERO.setScale(4);
        purchase.dueAmount = purchase.totalPayable;
        purchase.changeAmount = BigDecimal.ZERO.setScale(4);
        purchase.returnedAmount = BigDecimal.ZERO.setScale(4);
        purchase.createdByActorType = Objects.requireNonNull(actorType, "actorType is required");
        purchase.createdByActorId = Objects.requireNonNull(actorId, "actorId is required");
        purchase.createdAt = Objects.requireNonNull(now, "now is required");
        purchase.updatedAt = now;
        return purchase;
    }

    public void reviseDraft(Long supplierId, LocalDate purchaseDate, String supplierInvoiceReference, String notes,
                            String attachmentReference, BigDecimal subtotal, BigDecimal itemTaxTotal,
                            BigDecimal itemDiscountTotal, BigDecimal orderTax, BigDecimal shippingCharges,
                            BigDecimal otherCharges, BigDecimal discount, BigDecimal totalPayable, Instant now) {
        requireDraft();
        this.supplierId = Objects.requireNonNull(supplierId, "supplierId is required");
        this.purchaseDate = Objects.requireNonNull(purchaseDate, "purchaseDate is required");
        this.supplierInvoiceReference = optional(supplierInvoiceReference);
        this.notes = optional(notes);
        this.attachmentReference = optional(attachmentReference);
        this.subtotal = amount(subtotal);
        this.itemTaxTotal = amount(itemTaxTotal);
        this.itemDiscountTotal = amount(itemDiscountTotal);
        this.orderTax = amount(orderTax);
        this.shippingCharges = amount(shippingCharges);
        this.otherCharges = amount(otherCharges);
        this.discount = amount(discount);
        this.totalPayable = amount(totalPayable);
        this.dueAmount = totalPayable;
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void confirm(BigDecimal appliedPaidAmount, BigDecimal changeAmount, Instant now) {
        requireDraft();
        this.status = PurchaseStatus.CONFIRMED;
        this.paidAmount = amount(appliedPaidAmount);
        this.changeAmount = amount(changeAmount);
        recalculateDueAndPaymentStatus();
        this.confirmedAt = Objects.requireNonNull(now, "now is required");
        this.updatedAt = now;
    }

    public void applySupplierPayment(BigDecimal amountPaid, Instant now) {
        requireConfirmedOperational();
        BigDecimal amount = amount(amountPaid);
        if (amount.signum() <= 0 || amount.compareTo(dueAmount) > 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero and not exceed supplier due");
        }
        paidAmount = paidAmount.add(amount);
        recalculateDueAndPaymentStatus();
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void reverseSupplierPayment(BigDecimal amountPaid, Instant now) {
        requireConfirmedOperational();
        BigDecimal amount = amount(amountPaid);
        if (amount.signum() <= 0 || amount.compareTo(paidAmount) > 0) {
            throw new IllegalArgumentException("Payment reversal amount must be greater than zero and not exceed confirmed paid amount");
        }
        paidAmount = paidAmount.subtract(amount);
        recalculateDueAndPaymentStatus();
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public BigDecimal applyReturn(BigDecimal returnAmount, Instant now) {
        requireConfirmedOperational();
        BigDecimal amount = amount(returnAmount);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Return amount must be greater than zero");
        }
        BigDecimal dueBefore = dueAmount;
        returnedAmount = returnedAmount.add(amount);
        recalculateDueAndPaymentStatus();
        status = returnedAmount.compareTo(totalPayable) >= 0
                ? PurchaseStatus.RETURNED
                : PurchaseStatus.PARTIALLY_RETURNED;
        updatedAt = Objects.requireNonNull(now, "now is required");
        return dueBefore.subtract(dueAmount);
    }

    public boolean isDraft() {
        return status == PurchaseStatus.DRAFT;
    }

    public boolean isConfirmedOperational() {
        return status == PurchaseStatus.CONFIRMED
                || status == PurchaseStatus.PARTIALLY_RETURNED
                || status == PurchaseStatus.RETURNED;
    }

    private void recalculateDueAndPaymentStatus() {
        BigDecimal netLiability = totalPayable.subtract(returnedAmount);
        if (netLiability.signum() < 0) netLiability = BigDecimal.ZERO.setScale(4);
        dueAmount = netLiability.subtract(paidAmount);
        if (dueAmount.signum() < 0) dueAmount = BigDecimal.ZERO.setScale(4);

        if (dueAmount.signum() == 0) {
            paymentStatus = PurchasePaymentStatus.PAID;
        } else if (paidAmount.signum() > 0) {
            paymentStatus = PurchasePaymentStatus.PARTIALLY_PAID;
        } else {
            paymentStatus = PurchasePaymentStatus.UNPAID;
        }
    }

    private void requireDraft() {
        if (status != PurchaseStatus.DRAFT) {
            throw new IllegalStateException("Only Draft purchase can be edited or confirmed");
        }
    }

    private void requireConfirmedOperational() {
        if (!isConfirmedOperational()) {
            throw new IllegalStateException("Purchase is not eligible for this operation");
        }
    }

    private static BigDecimal amount(BigDecimal value) {
        return Objects.requireNonNull(value, "amount is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
