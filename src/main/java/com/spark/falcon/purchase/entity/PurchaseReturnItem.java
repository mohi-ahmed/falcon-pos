package com.spark.falcon.purchase.entity;

import com.spark.falcon.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "purchase_return_items", indexes = {
        @Index(name = "idx_purchase_return_item_return", columnList = "purchase_return_id"),
        @Index(name = "idx_purchase_return_item_purchase_item", columnList = "purchase_item_id"),
        @Index(name = "idx_purchase_return_item_batch", columnList = "product_batch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_return_id", nullable = false, updatable = false)
    private Long purchaseReturnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_return_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_return_item_return"))
    @Getter(AccessLevel.NONE)
    private PurchaseReturn purchaseReturnReference;

    @Column(name = "purchase_item_id", nullable = false, updatable = false)
    private Long purchaseItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_return_item_purchase_item"))
    @Getter(AccessLevel.NONE)
    private PurchaseItem purchaseItemReference;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_return_item_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "product_batch_id", updatable = false)
    private Long productBatchId;

    @Column(name = "entered_return_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal enteredReturnQuantity;

    @Column(name = "return_unit_id", nullable = false, updatable = false)
    private Long returnUnitId;

    @Column(name = "conversion_factor", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal conversionFactor;

    @Column(name = "base_inventory_unit_id", nullable = false, updatable = false)
    private Long baseInventoryUnitId;

    @Column(name = "base_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal baseQuantity;

    @Column(name = "supplier_unit_cost_snapshot", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal supplierUnitCostSnapshot;

    @Column(name = "allocated_landed_unit_cost_snapshot", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal allocatedLandedUnitCostSnapshot;

    @Column(name = "return_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal returnAmount;

    @Column(name = "stock_movement_id")
    private Long stockMovementId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PurchaseReturnItem create(Long purchaseReturnId, Long purchaseItemId, Long productVariantId,
                                            Long productBatchId, BigDecimal enteredReturnQuantity, Long returnUnitId,
                                            BigDecimal conversionFactor, Long baseInventoryUnitId,
                                            BigDecimal baseQuantity, BigDecimal supplierUnitCostSnapshot,
                                            BigDecimal allocatedLandedUnitCostSnapshot, BigDecimal returnAmount,
                                            Instant now) {
        PurchaseReturnItem item = new PurchaseReturnItem();
        item.purchaseReturnId = Objects.requireNonNull(purchaseReturnId, "purchaseReturnId is required");
        item.purchaseItemId = Objects.requireNonNull(purchaseItemId, "purchaseItemId is required");
        item.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        item.productBatchId = productBatchId;
        item.enteredReturnQuantity = Objects.requireNonNull(enteredReturnQuantity, "enteredReturnQuantity is required");
        item.returnUnitId = Objects.requireNonNull(returnUnitId, "returnUnitId is required");
        item.conversionFactor = Objects.requireNonNull(conversionFactor, "conversionFactor is required");
        item.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        item.baseQuantity = Objects.requireNonNull(baseQuantity, "baseQuantity is required");
        item.supplierUnitCostSnapshot = Objects.requireNonNull(supplierUnitCostSnapshot, "supplierUnitCostSnapshot is required");
        item.allocatedLandedUnitCostSnapshot = Objects.requireNonNull(allocatedLandedUnitCostSnapshot, "allocatedLandedUnitCostSnapshot is required");
        item.returnAmount = Objects.requireNonNull(returnAmount, "returnAmount is required");
        item.createdAt = Objects.requireNonNull(now, "now is required");
        return item;
    }

    public void attachStockMovement(Long stockMovementId) {
        if (this.stockMovementId != null) throw new IllegalStateException("Purchase return stock movement is already attached");
        this.stockMovementId = Objects.requireNonNull(stockMovementId, "stockMovementId is required");
    }
}
