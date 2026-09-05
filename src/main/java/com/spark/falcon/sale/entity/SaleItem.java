package com.spark.falcon.sale.entity;

import com.spark.falcon.product.entity.ProductVariant;
import com.spark.falcon.settings.entity.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(name = "sale_items", indexes = {
        @Index(name = "idx_sale_item_sale", columnList = "sale_id"),
        @Index(name = "idx_sale_item_variant", columnList = "product_variant_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleItem {

    private static final int QUANTITY_SCALE = 8;
    private static final int MONEY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false, updatable = false)
    private Long saleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_item_sale"))
    @Getter(AccessLevel.NONE)
    private Sale saleReference;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_item_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "product_name_snapshot", nullable = false, updatable = false, length = 180)
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot", nullable = false, updatable = false, length = 180)
    private String variantNameSnapshot;

    @Column(name = "product_code_snapshot", nullable = false, updatable = false, length = 100)
    private String productCodeSnapshot;

    @Column(name = "entered_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal enteredQuantity;

    @Column(name = "entered_unit_id", nullable = false, updatable = false)
    private Long enteredUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_item_entered_unit"))
    @Getter(AccessLevel.NONE)
    private Unit enteredUnitReference;

    @Column(name = "conversion_factor", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal conversionFactor;

    @Column(name = "base_inventory_unit_id", nullable = false, updatable = false)
    private Long baseInventoryUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_inventory_unit_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_sale_item_base_unit"))
    @Getter(AccessLevel.NONE)
    private Unit baseInventoryUnitReference;

    @Column(name = "base_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal baseQuantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "gross_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(name = "tax_rate", nullable = false, updatable = false, precision = 10, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_method", nullable = false, updatable = false, length = 30)
    private String taxMethod;

    @Column(name = "tax_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "line_payable", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal linePayable;

    @Column(name = "stock_movement_id")
    private Long stockMovementId;

    @Column(name = "weighted_average_cost_snapshot", precision = 19, scale = 4)
    private BigDecimal weightedAverageCostSnapshot;

    @Column(name = "cogs_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal cogsAmount;

    @Column(name = "returned_base_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal returnedBaseQuantity;

    public static SaleItem create(Long saleId,
                                  Long productVariantId,
                                  String productName,
                                  String variantName,
                                  String productCode,
                                  BigDecimal enteredQuantity,
                                  Long enteredUnitId,
                                  BigDecimal conversionFactor,
                                  Long baseInventoryUnitId,
                                  BigDecimal baseQuantity,
                                  BigDecimal unitPrice,
                                  BigDecimal grossAmount,
                                  BigDecimal discountAmount,
                                  BigDecimal taxRate,
                                  String taxMethod,
                                  BigDecimal taxAmount,
                                  BigDecimal linePayable) {
        SaleItem item = new SaleItem();
        item.saleId = Objects.requireNonNull(saleId, "saleId is required");
        item.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        item.productNameSnapshot = required(productName, "productName");
        item.variantNameSnapshot = required(variantName, "variantName");
        item.productCodeSnapshot = required(productCode, "productCode");
        item.enteredQuantity = positiveQuantity(enteredQuantity, "enteredQuantity");
        item.enteredUnitId = Objects.requireNonNull(enteredUnitId, "enteredUnitId is required");
        item.conversionFactor = positiveQuantity(conversionFactor, "conversionFactor");
        item.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        item.baseQuantity = positiveQuantity(baseQuantity, "baseQuantity");
        item.unitPrice = money(unitPrice);
        item.grossAmount = money(grossAmount);
        item.discountAmount = money(discountAmount);
        item.taxRate = taxRate == null ? zeroMoney() : taxRate.setScale(4, RoundingMode.HALF_UP);
        item.taxMethod = taxMethod == null || taxMethod.isBlank() ? "NONE" : taxMethod.trim();
        item.taxAmount = money(taxAmount);
        item.linePayable = money(linePayable);
        item.cogsAmount = zeroMoney();
        item.returnedBaseQuantity = zeroQuantity();
        return item;
    }

    public void attachInventoryPosting(Long movementId, BigDecimal weightedAverageCost) {
        if (stockMovementId != null || weightedAverageCostSnapshot != null) {
            throw new IllegalStateException("Sale Item inventory posting is already attached");
        }
        stockMovementId = Objects.requireNonNull(movementId, "movementId is required");
        weightedAverageCostSnapshot = money(weightedAverageCost);
        cogsAmount = baseQuantity.multiply(weightedAverageCostSnapshot).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public void registerReturn(BigDecimal returnedBaseQuantity) {
        BigDecimal quantity = positiveQuantity(returnedBaseQuantity, "returnedBaseQuantity");
        BigDecimal resulting = this.returnedBaseQuantity.add(quantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (resulting.compareTo(baseQuantity) > 0) {
            throw new IllegalArgumentException("Sales Return quantity exceeds Sale Item quantity");
        }
        this.returnedBaseQuantity = resulting;
    }

    public void reverseReturn(BigDecimal returnedBaseQuantity) {
        BigDecimal quantity = positiveQuantity(returnedBaseQuantity, "returnedBaseQuantity");
        if (quantity.compareTo(this.returnedBaseQuantity) > 0) {
            throw new IllegalArgumentException("Sales Return reversal exceeds returned Sale Item quantity");
        }
        this.returnedBaseQuantity = this.returnedBaseQuantity.subtract(quantity)
                .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal remainingReturnableBaseQuantity() {
        return baseQuantity.subtract(returnedBaseQuantity).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateReturnAmount(BigDecimal returnBaseQuantity) {
        BigDecimal quantity = positiveQuantity(returnBaseQuantity, "returnBaseQuantity");
        if (quantity.compareTo(remainingReturnableBaseQuantity()) > 0) {
            throw new IllegalArgumentException("Return quantity exceeds remaining eligible quantity");
        }
        return linePayable.multiply(quantity)
                .divide(baseQuantity, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveQuantity(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Money value must not be negative");
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal zeroQuantity() {
        return BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
