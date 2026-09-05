package com.spark.falcon.sale.entity;

import com.spark.falcon.inventory.entity.ProductBatch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "sale_item_batch_allocations", indexes = {
        @Index(name = "idx_sale_item_batch_item", columnList = "sale_item_id"),
        @Index(name = "idx_sale_item_batch_batch", columnList = "product_batch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleItemBatchAllocation {

    private static final int QUANTITY_SCALE = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_item_id", nullable = false, updatable = false)
    private Long saleItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_batch_allocation_item"))
    @Getter(AccessLevel.NONE)
    private SaleItem saleItemReference;

    @Column(name = "product_batch_id", nullable = false, updatable = false)
    private Long productBatchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_batch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_batch_allocation_batch"))
    @Getter(AccessLevel.NONE)
    private ProductBatch productBatchReference;

    @Column(name = "batch_number_snapshot", nullable = false, updatable = false, length = 100)
    private String batchNumberSnapshot;

    @Column(name = "expiry_date_snapshot", updatable = false)
    private LocalDate expiryDateSnapshot;

    @Column(name = "base_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal baseQuantity;

    @Column(name = "returned_base_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal returnedBaseQuantity;

    public static SaleItemBatchAllocation create(Long saleItemId,
                                                 Long productBatchId,
                                                 String batchNumber,
                                                 LocalDate expiryDate,
                                                 BigDecimal baseQuantity) {
        SaleItemBatchAllocation value = new SaleItemBatchAllocation();
        value.saleItemId = Objects.requireNonNull(saleItemId, "saleItemId is required");
        value.productBatchId = Objects.requireNonNull(productBatchId, "productBatchId is required");
        value.batchNumberSnapshot = required(batchNumber, "batchNumber");
        value.expiryDateSnapshot = expiryDate;
        value.baseQuantity = positive(baseQuantity);
        value.returnedBaseQuantity = BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        return value;
    }

    public void registerReturn(BigDecimal quantity) {
        BigDecimal normalized = positive(quantity);
        BigDecimal resulting = returnedBaseQuantity.add(normalized).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (resulting.compareTo(baseQuantity) > 0) {
            throw new IllegalArgumentException("Sales Return exceeds quantity allocated from this batch");
        }
        returnedBaseQuantity = resulting;
    }

    public void reverseReturn(BigDecimal quantity) {
        BigDecimal normalized = positive(quantity);
        if (normalized.compareTo(returnedBaseQuantity) > 0) {
            throw new IllegalArgumentException("Sales Return reversal exceeds returned batch quantity");
        }
        returnedBaseQuantity = returnedBaseQuantity.subtract(normalized)
                .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal remainingReturnableBaseQuantity() {
        return baseQuantity.subtract(returnedBaseQuantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("baseQuantity must be greater than zero");
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
