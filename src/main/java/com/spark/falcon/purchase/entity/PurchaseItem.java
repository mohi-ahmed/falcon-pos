package com.spark.falcon.purchase.entity;

import com.spark.falcon.inventory.entity.ProductBatch;
import com.spark.falcon.product.entity.ProductVariant;
import com.spark.falcon.settings.entity.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "purchase_items", indexes = {
        @Index(name = "idx_purchase_item_purchase", columnList = "purchase_id"),
        @Index(name = "idx_purchase_item_variant", columnList = "product_variant_id"),
        @Index(name = "idx_purchase_item_batch", columnList = "product_batch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id", nullable = false, updatable = false)
    private Long purchaseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_item_purchase"))
    @Getter(AccessLevel.NONE)
    private Purchase purchaseReference;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_item_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "entered_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal enteredQuantity;

    @Column(name = "entered_unit_id", nullable = false, updatable = false)
    private Long enteredUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_item_entered_unit"))
    @Getter(AccessLevel.NONE)
    private Unit enteredUnitReference;

    @Column(name = "conversion_factor", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal conversionFactor;

    @Column(name = "base_inventory_unit_id", nullable = false, updatable = false)
    private Long baseInventoryUnitId;

    @Column(name = "base_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal baseQuantity;

    @Column(name = "unit_cost", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "intended_selling_price", precision = 19, scale = 4, updatable = false)
    private BigDecimal intendedSellingPrice;

    @Column(name = "item_tax", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal itemTax;

    @Column(name = "item_discount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal itemDiscount;

    @Column(name = "line_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal lineAmount;

    @Column(name = "allocated_order_tax", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal allocatedOrderTax;

    @Column(name = "allocated_purchase_discount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal allocatedPurchaseDiscount;

    @Column(name = "allocated_shipping", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal allocatedShipping;

    @Column(name = "allocated_other_charges", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal allocatedOtherCharges;

    @Column(name = "landed_inventory_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal landedInventoryAmount;

    @Column(name = "base_unit_landed_cost", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal baseUnitLandedCost;

    @Column(name = "batch_number", length = 100, updatable = false)
    private String batchNumber;

    @Column(name = "manufacturing_date", updatable = false)
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date", updatable = false)
    private LocalDate expiryDate;

    @Column(name = "product_batch_id")
    private Long productBatchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_batch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_item_product_batch"))
    @Getter(AccessLevel.NONE)
    private ProductBatch productBatchReference;

    @Column(name = "stock_movement_id")
    private Long stockMovementId;

    @Column(name = "weighted_average_cost_after_confirmation", precision = 19, scale = 4)
    private BigDecimal weightedAverageCostAfterConfirmation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PurchaseItem create(Long purchaseId, Long productVariantId, BigDecimal enteredQuantity,
                                      Long enteredUnitId, BigDecimal conversionFactor, Long baseInventoryUnitId,
                                      BigDecimal baseQuantity, BigDecimal unitCost, BigDecimal intendedSellingPrice,
                                      BigDecimal itemTax, BigDecimal itemDiscount, BigDecimal lineAmount,
                                      BigDecimal allocatedOrderTax, BigDecimal allocatedPurchaseDiscount,
                                      BigDecimal allocatedShipping, BigDecimal allocatedOtherCharges,
                                      BigDecimal landedInventoryAmount, BigDecimal baseUnitLandedCost,
                                      String batchNumber, LocalDate manufacturingDate, LocalDate expiryDate,
                                      Instant now) {
        PurchaseItem item = new PurchaseItem();
        item.purchaseId = Objects.requireNonNull(purchaseId, "purchaseId is required");
        item.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        item.enteredQuantity = Objects.requireNonNull(enteredQuantity, "enteredQuantity is required");
        item.enteredUnitId = Objects.requireNonNull(enteredUnitId, "enteredUnitId is required");
        item.conversionFactor = Objects.requireNonNull(conversionFactor, "conversionFactor is required");
        item.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        item.baseQuantity = Objects.requireNonNull(baseQuantity, "baseQuantity is required");
        item.unitCost = Objects.requireNonNull(unitCost, "unitCost is required");
        item.intendedSellingPrice = intendedSellingPrice;
        item.itemTax = Objects.requireNonNull(itemTax, "itemTax is required");
        item.itemDiscount = Objects.requireNonNull(itemDiscount, "itemDiscount is required");
        item.lineAmount = Objects.requireNonNull(lineAmount, "lineAmount is required");
        item.allocatedOrderTax = Objects.requireNonNull(allocatedOrderTax, "allocatedOrderTax is required");
        item.allocatedPurchaseDiscount = Objects.requireNonNull(allocatedPurchaseDiscount, "allocatedPurchaseDiscount is required");
        item.allocatedShipping = Objects.requireNonNull(allocatedShipping, "allocatedShipping is required");
        item.allocatedOtherCharges = Objects.requireNonNull(allocatedOtherCharges, "allocatedOtherCharges is required");
        item.landedInventoryAmount = Objects.requireNonNull(landedInventoryAmount, "landedInventoryAmount is required");
        item.baseUnitLandedCost = Objects.requireNonNull(baseUnitLandedCost, "baseUnitLandedCost is required");
        item.batchNumber = optional(batchNumber);
        item.manufacturingDate = manufacturingDate;
        item.expiryDate = expiryDate;
        item.createdAt = Objects.requireNonNull(now, "now is required");
        return item;
    }

    public void attachInventoryPosting(Long productBatchId, Long stockMovementId) {
        if (this.stockMovementId != null) {
            throw new IllegalStateException("Purchase item inventory posting is already attached");
        }
        this.productBatchId = productBatchId;
        this.stockMovementId = Objects.requireNonNull(stockMovementId, "stockMovementId is required");
    }

    public void captureWeightedAverageCostAfterConfirmation(BigDecimal weightedAverageCostAfterConfirmation) {
        if (this.weightedAverageCostAfterConfirmation != null) {
            throw new IllegalStateException("Purchase item Weighted Average Cost snapshot is already captured");
        }
        this.weightedAverageCostAfterConfirmation = Objects.requireNonNull(
                        weightedAverageCostAfterConfirmation, "weightedAverageCostAfterConfirmation is required")
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
