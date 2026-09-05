package com.spark.falcon.inventory.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "product_batches", indexes = {
        @Index(name = "idx_product_batch_branch_variant", columnList = "business_id,branch_id,product_variant_id"),
        @Index(name = "idx_product_batch_number", columnList = "batch_number"),
        @Index(name = "idx_product_batch_expiry", columnList = "expiry_date"),
        @Index(name = "idx_product_batch_source_purchase_item", columnList = "source_purchase_item_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_batch_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_product_batch_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "supplier_id", updatable = false)
    private Long supplierId;

    @Column(name = "source_purchase_item_id", updatable = false)
    private Long sourcePurchaseItemId;

    @Column(name = "batch_number", nullable = false, updatable = false, length = 100)
    private String batchNumber;

    @Column(name = "manufacturing_date", updatable = false)
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date", updatable = false)
    private LocalDate expiryDate;

    @Column(name = "received_base_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal receivedBaseQuantity;

    @Column(name = "available_base_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal availableBaseQuantity;

    @Column(name="reserved_base_quantity", nullable=false, precision=19, scale=8,
            columnDefinition="numeric(19,8) default 0")
    private BigDecimal reservedBaseQuantity = BigDecimal.ZERO.setScale(8);

    @Column(name = "original_purchase_unit_cost", precision = 19, scale = 4, updatable = false)
    private BigDecimal originalPurchaseUnitCost;

    @Column(name = "allocated_landed_unit_cost", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal allocatedLandedUnitCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProductBatchStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static ProductBatch create(Long businessId, Long branchId, Long productVariantId,
                                      Long supplierId, Long sourcePurchaseItemId, String batchNumber,
                                      LocalDate manufacturingDate, LocalDate expiryDate,
                                      BigDecimal receivedBaseQuantity, BigDecimal originalPurchaseUnitCost,
                                      BigDecimal allocatedLandedUnitCost, ProductBatchStatus status, Instant now) {
        ProductBatch batch = new ProductBatch();
        batch.businessId = Objects.requireNonNull(businessId, "businessId is required");
        batch.branchId = Objects.requireNonNull(branchId, "branchId is required");
        batch.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        batch.supplierId = supplierId;
        batch.sourcePurchaseItemId = sourcePurchaseItemId;
        batch.batchNumber = required(batchNumber, "batchNumber");
        batch.manufacturingDate = manufacturingDate;
        batch.expiryDate = expiryDate;
        batch.receivedBaseQuantity = positive(receivedBaseQuantity, "receivedBaseQuantity");
        batch.availableBaseQuantity = receivedBaseQuantity;
        batch.reservedBaseQuantity = BigDecimal.ZERO.setScale(8);
        batch.originalPurchaseUnitCost = notNegativeOrNull(originalPurchaseUnitCost, "originalPurchaseUnitCost");
        batch.allocatedLandedUnitCost = notNegative(allocatedLandedUnitCost, "allocatedLandedUnitCost");
        batch.status = Objects.requireNonNull(status, "status is required");
        batch.createdAt = Objects.requireNonNull(now, "now is required");
        batch.updatedAt = now;
        return batch;
    }

    public static ProductBatch createTransferDestination(Long businessId, Long branchId, Long productVariantId,
            String batchNumber, LocalDate manufacturingDate, LocalDate expiryDate,
            BigDecimal originalCost, BigDecimal transferCost, Instant now) {
        ProductBatch batch = new ProductBatch(); batch.businessId=businessId; batch.branchId=branchId;
        batch.productVariantId=productVariantId; batch.batchNumber=required(batchNumber,"batchNumber");
        batch.manufacturingDate=manufacturingDate; batch.expiryDate=expiryDate;
        batch.receivedBaseQuantity=BigDecimal.ZERO.setScale(8); batch.availableBaseQuantity=BigDecimal.ZERO.setScale(8);
        batch.reservedBaseQuantity=BigDecimal.ZERO.setScale(8); batch.originalPurchaseUnitCost=originalCost;
        batch.allocatedLandedUnitCost=notNegative(transferCost,"transferCost"); batch.status=ProductBatchStatus.DEPLETED;
        batch.createdAt=now; batch.updatedAt=now; return batch;
    }

    public void returnToSupplier(BigDecimal quantity, Instant now) {
        positive(quantity, "quantity");
        if (quantity.compareTo(availableBaseQuantity) > 0) {
            throw new IllegalArgumentException("Returned quantity exceeds available batch quantity");
        }
        availableBaseQuantity = availableBaseQuantity.subtract(quantity);
        if (availableBaseQuantity.signum() == 0) {
            status = ProductBatchStatus.RETURNED;
        }
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void reduceAvailable(BigDecimal quantity, Instant now) {
        positive(quantity, "quantity");
        if (quantity.compareTo(availableBaseQuantity) > 0) {
            throw new IllegalArgumentException("Returned quantity exceeds available batch quantity");
        }
        availableBaseQuantity = availableBaseQuantity.subtract(quantity);
        if (availableBaseQuantity.signum() == 0) {
            status = ProductBatchStatus.DEPLETED;
        }
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void adjustAvailable(BigDecimal quantityDifference, Instant now) {
        Objects.requireNonNull(quantityDifference, "quantityDifference is required");
        BigDecimal resulting = availableBaseQuantity.add(quantityDifference);
        if (resulting.signum() < 0) {
            throw new IllegalArgumentException("Batch adjustment would make available quantity negative");
        }
        availableBaseQuantity = resulting;
        if (resulting.signum() == 0) {
            status = ProductBatchStatus.DEPLETED;
        } else if (status == ProductBatchStatus.DEPLETED) {
            status = ProductBatchStatus.ACTIVE;
        }
        updatedAt = Objects.requireNonNull(now, "now is required");
    }
    public void receiveTransfer(BigDecimal quantity, Instant now) { positive(quantity,"transferQuantity");receivedBaseQuantity=receivedBaseQuantity.add(quantity);adjustAvailable(quantity,now); }

    public BigDecimal availableToReserve() { return availableBaseQuantity.subtract(reservedBaseQuantity == null ? BigDecimal.ZERO.setScale(8) : reservedBaseQuantity); }
    public void reserve(BigDecimal quantity, Instant now) { positive(quantity,"reservedQuantity"); if(quantity.compareTo(availableToReserve())>0)throw new IllegalArgumentException("Transfer quantity exceeds unreserved batch stock");reservedBaseQuantity=reservedBaseQuantity.add(quantity);updatedAt=Objects.requireNonNull(now); }
    public void releaseReservation(BigDecimal quantity, Instant now) { positive(quantity,"releasedQuantity");if(quantity.compareTo(reservedBaseQuantity)>0)throw new IllegalArgumentException("Released quantity exceeds batch reservation");reservedBaseQuantity=reservedBaseQuantity.subtract(quantity);updatedAt=Objects.requireNonNull(now); }


    public void restoreStatusAfterReversal(ProductBatchStatus previousStatus, LocalDate effectiveDate, Instant now) {
        Objects.requireNonNull(previousStatus, "previousStatus is required");
        if (availableBaseQuantity.signum() == 0) {
            status = ProductBatchStatus.DEPLETED;
        } else if (expiryDate != null && effectiveDate != null && expiryDate.isBefore(effectiveDate)) {
            status = ProductBatchStatus.EXPIRED;
        } else {
            status = previousStatus == ProductBatchStatus.DEPLETED ? ProductBatchStatus.ACTIVE : previousStatus;
        }
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return value;
    }

    private static BigDecimal notNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static BigDecimal notNegativeOrNull(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
