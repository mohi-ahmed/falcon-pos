package com.spark.falcon.sale.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "sale_return_items", indexes = {
        @Index(name = "idx_sale_return_item_return", columnList = "sale_return_id"),
        @Index(name = "idx_sale_return_item_sale_item", columnList = "sale_item_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReturnItem {

    private static final int QUANTITY_SCALE = 8;
    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_return_id", nullable = false, updatable = false)
    private Long saleReturnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_return_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_return_item_return"))
    @Getter(AccessLevel.NONE)
    private SaleReturn saleReturnReference;

    @Column(name = "sale_item_id", nullable = false, updatable = false)
    private Long saleItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_return_item_sale_item"))
    @Getter(AccessLevel.NONE)
    private SaleItem saleItemReference;

    @Column(name = "sale_item_batch_allocation_id", updatable = false)
    private Long saleItemBatchAllocationId;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @Column(name = "product_batch_id", updatable = false)
    private Long productBatchId;

    @Column(name = "batch_number_snapshot", length = 100, updatable = false)
    private String batchNumberSnapshot;

    @Column(name = "expiry_date_snapshot", updatable = false)
    private LocalDate expiryDateSnapshot;

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

    @Column(name = "return_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal returnAmount;

    @Column(name = "preserved_financial_cost_snapshot", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal preservedFinancialCostSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private SaleReturnItemCondition condition;

    @Column(length = 500, updatable = false)
    private String reason;

    @Column(name = "stock_movement_id")
    private Long stockMovementId;

    public static SaleReturnItem create(Long saleReturnId,
                                        Long saleItemId,
                                        Long saleItemBatchAllocationId,
                                        Long productVariantId,
                                        Long productBatchId,
                                        String batchNumber,
                                        LocalDate expiryDate,
                                        BigDecimal enteredReturnQuantity,
                                        Long returnUnitId,
                                        BigDecimal conversionFactor,
                                        Long baseInventoryUnitId,
                                        BigDecimal baseQuantity,
                                        BigDecimal returnAmount,
                                        BigDecimal preservedFinancialCostSnapshot,
                                        SaleReturnItemCondition condition,
                                        String reason) {
        SaleReturnItem value = new SaleReturnItem();
        value.saleReturnId = Objects.requireNonNull(saleReturnId, "saleReturnId is required");
        value.saleItemId = Objects.requireNonNull(saleItemId, "saleItemId is required");
        value.saleItemBatchAllocationId = saleItemBatchAllocationId;
        value.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        value.productBatchId = productBatchId;
        value.batchNumberSnapshot = optional(batchNumber);
        value.expiryDateSnapshot = expiryDate;
        value.enteredReturnQuantity = positiveQuantity(enteredReturnQuantity);
        value.returnUnitId = Objects.requireNonNull(returnUnitId, "returnUnitId is required");
        value.conversionFactor = positiveQuantity(conversionFactor);
        value.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        value.baseQuantity = positiveQuantity(baseQuantity);
        value.returnAmount = money(returnAmount);
        value.preservedFinancialCostSnapshot = money(preservedFinancialCostSnapshot);
        value.condition = Objects.requireNonNull(condition, "condition is required");
        value.reason = optional(reason);
        return value;
    }

    public void attachStockMovement(Long movementId) {
        if (stockMovementId != null) throw new IllegalStateException("Stock Movement is already attached");
        stockMovementId = Objects.requireNonNull(movementId, "movementId is required");
    }

    private static BigDecimal positiveQuantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Money value must not be negative");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
