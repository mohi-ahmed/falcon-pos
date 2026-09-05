package com.spark.falcon.inventory.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "branch_product_stocks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_product_stock", columnNames = {"business_id", "branch_id", "product_variant_id"})
}, indexes = {
        @Index(name = "idx_branch_product_stock_branch", columnList = "business_id,branch_id"),
        @Index(name = "idx_branch_product_stock_variant", columnList = "product_variant_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchProductStock {

    private static final int QUANTITY_SCALE = 8;
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
            foreignKey = @ForeignKey(name = "fk_branch_product_stock_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_product_stock_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "base_inventory_unit_id", nullable = false, updatable = false)
    private Long baseInventoryUnitId;

    @Column(name = "base_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal baseQuantity;

    @Column(name = "weighted_average_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal weightedAverageCost;

    @Column(name = "inventory_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal inventoryValue;

    @Column(name = "reserved_base_quantity", nullable = false, precision = 19, scale = 8,
            columnDefinition = "numeric(19,8) default 0")
    private BigDecimal reservedBaseQuantity = quantityZero();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static BranchProductStock create(Long businessId, Long branchId, Long productVariantId,
                                            Long baseInventoryUnitId, Instant now) {
        BranchProductStock stock = new BranchProductStock();
        stock.businessId = Objects.requireNonNull(businessId, "businessId is required");
        stock.branchId = Objects.requireNonNull(branchId, "branchId is required");
        stock.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        stock.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        stock.baseQuantity = quantityZero();
        stock.weightedAverageCost = moneyZero();
        stock.inventoryValue = moneyZero();
        stock.reservedBaseQuantity = quantityZero();
        stock.createdAt = Objects.requireNonNull(now, "now is required");
        stock.updatedAt = now;
        return stock;
    }

    public void receive(BigDecimal receivedBaseQuantity, BigDecimal landedBaseUnitCost, Instant now) {
        requirePositive(receivedBaseQuantity, "receivedBaseQuantity");
        requireNotNegative(landedBaseUnitCost, "landedBaseUnitCost");

        BigDecimal incomingValue = receivedBaseQuantity.multiply(landedBaseUnitCost)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newQuantity = baseQuantity.add(receivedBaseQuantity);
        BigDecimal newInventoryValue = inventoryValue.add(incomingValue)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newWeightedAverageCost = newInventoryValue.divide(
                newQuantity, MONEY_SCALE, RoundingMode.HALF_UP);

        baseQuantity = newQuantity;
        inventoryValue = newInventoryValue;
        weightedAverageCost = newWeightedAverageCost;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public BigDecimal removeAtPreservedCost(BigDecimal removedBaseQuantity, BigDecimal preservedUnitCost, Instant now) {
        requirePositive(removedBaseQuantity, "removedBaseQuantity");
        requireNotNegative(preservedUnitCost, "preservedUnitCost");
        if (removedBaseQuantity.compareTo(baseQuantity) > 0) {
            throw new IllegalArgumentException("removedBaseQuantity exceeds available stock");
        }

        BigDecimal valueRemoved = removedBaseQuantity.multiply(preservedUnitCost)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newQuantity = baseQuantity.subtract(removedBaseQuantity);
        BigDecimal newInventoryValue = inventoryValue.subtract(valueRemoved)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (newQuantity.signum() == 0) {
            baseQuantity = quantityZero();
            inventoryValue = moneyZero();
            weightedAverageCost = moneyZero();
        } else {
            if (newInventoryValue.signum() < 0) {
                throw new IllegalArgumentException("Purchase return would make inventory value negative");
            }
            baseQuantity = newQuantity;
            inventoryValue = newInventoryValue;
            weightedAverageCost = newInventoryValue.divide(newQuantity, MONEY_SCALE, RoundingMode.HALF_UP);
        }
        updatedAt = Objects.requireNonNull(now, "now is required");
        return valueRemoved;
    }

    public BigDecimal decreaseAtWeightedAverageCost(BigDecimal removedBaseQuantity, Instant now) {
        requirePositive(removedBaseQuantity, "removedBaseQuantity");
        if (removedBaseQuantity.compareTo(baseQuantity) > 0) {
            throw new IllegalArgumentException("removedBaseQuantity exceeds available stock");
        }
        BigDecimal costSnapshot = weightedAverageCost;
        BigDecimal valueRemoved = removedBaseQuantity.multiply(costSnapshot)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        baseQuantity = baseQuantity.subtract(removedBaseQuantity);
        inventoryValue = inventoryValue.subtract(valueRemoved).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (baseQuantity.signum() == 0) {
            baseQuantity = quantityZero();
            inventoryValue = moneyZero();
            weightedAverageCost = moneyZero();
        }
        updatedAt = Objects.requireNonNull(now, "now is required");
        return valueRemoved;
    }

    public BigDecimal availableToReserve() { return baseQuantity.subtract(reservedBaseQuantity == null ? quantityZero() : reservedBaseQuantity); }

    public void reserve(BigDecimal quantity, Instant now) {
        requirePositive(quantity, "reservedQuantity");
        if (quantity.compareTo(availableToReserve()) > 0) throw new IllegalArgumentException("Transfer quantity exceeds unreserved stock");
        reservedBaseQuantity = (reservedBaseQuantity == null ? quantityZero() : reservedBaseQuantity).add(quantity); updatedAt = Objects.requireNonNull(now);
    }

    public void releaseReservation(BigDecimal quantity, Instant now) {
        requirePositive(quantity, "releasedQuantity");
        if (quantity.compareTo(reservedBaseQuantity) > 0) throw new IllegalArgumentException("Released quantity exceeds reservation");
        reservedBaseQuantity = reservedBaseQuantity.subtract(quantity); updatedAt = Objects.requireNonNull(now);
    }

    private static BigDecimal quantityZero() {
        return BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
    }

    private static void requireNotNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
