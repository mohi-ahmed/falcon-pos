package com.spark.falcon.inventory.entity;

import com.spark.falcon.branch.entity.Branch;
import com.spark.falcon.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "stock_movements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_movement_posting_key", columnNames = {"business_id", "branch_id", "posting_key"})
}, indexes = {
        @Index(name = "idx_stock_movement_branch_time", columnList = "business_id,branch_id,posted_at"),
        @Index(name = "idx_stock_movement_variant_time", columnList = "product_variant_id,posted_at"),
        @Index(name = "idx_stock_movement_batch_time", columnList = "product_batch_id,posted_at"),
        @Index(name = "idx_stock_movement_source", columnList = "source_type,source_reference_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_stock_movement_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(name = "branch_product_stock_id", nullable = false, updatable = false)
    private Long branchProductStockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_product_stock_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_stock_movement_stock"))
    @Getter(AccessLevel.NONE)
    private BranchProductStock branchProductStockReference;

    @Column(name = "product_variant_id", nullable = false, updatable = false)
    private Long productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_stock_movement_variant"))
    @Getter(AccessLevel.NONE)
    private ProductVariant productVariantReference;

    @Column(name = "product_batch_id", updatable = false)
    private Long productBatchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_batch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_stock_movement_batch"))
    @Getter(AccessLevel.NONE)
    private ProductBatch productBatchReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 40)
    private StockMovementType movementType;

    @Column(name = "entered_quantity", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal enteredQuantity;

    @Column(name = "entered_unit_id", nullable = false, updatable = false)
    private Long enteredUnitId;

    @Column(name = "conversion_factor", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal conversionFactor;

    @Column(name = "base_inventory_unit_id", nullable = false, updatable = false)
    private Long baseInventoryUnitId;

    @Column(name = "base_quantity_change", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal baseQuantityChange;

    @Column(name = "quantity_before", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal quantityAfter;

    @Column(name = "financial_unit_cost_snapshot", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal financialUnitCostSnapshot;

    @Column(name = "inventory_value_change", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal inventoryValueChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false, length = 40)
    private StockSourceType sourceType;

    @Column(name = "source_reference_id", nullable = false, updatable = false, length = 100)
    private String sourceReferenceId;

    @Column(name = "source_line_reference", updatable = false, length = 100)
    private String sourceLineReference;

    @Column(name = "posting_key", nullable = false, updatable = false, length = 120)
    private String postingKey;

    @Column(name = "reversal_of_movement_id", updatable = false)
    private Long reversalOfMovementId;

    @Column(name = "reason", updatable = false, length = 240)
    private String reason;

    @Column(name = "notes", updatable = false, length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "posted_by_actor_type", nullable = false, updatable = false, length = 20)
    private InventoryActorType postedByActorType;

    @Column(name = "posted_by_actor_id", nullable = false, updatable = false)
    private Long postedByActorId;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    public static StockMovement inbound(Long businessId, Long branchId, Long branchProductStockId,
                                        Long productVariantId, Long productBatchId,
                                        StockMovementType movementType, BigDecimal enteredQuantity,
                                        Long enteredUnitId, BigDecimal conversionFactor,
                                        Long baseInventoryUnitId, BigDecimal baseQuantityChange,
                                        BigDecimal quantityBefore, BigDecimal quantityAfter,
                                        BigDecimal financialUnitCostSnapshot, BigDecimal inventoryValueChange,
                                        StockSourceType sourceType, String sourceReferenceId,
                                        String sourceLineReference, String postingKey,
                                        InventoryActorType actorType, Long actorId,
                                        String reason, String notes, Instant postedAt) {
        StockMovement movement = new StockMovement();
        movement.businessId = Objects.requireNonNull(businessId, "businessId is required");
        movement.branchId = Objects.requireNonNull(branchId, "branchId is required");
        movement.branchProductStockId = Objects.requireNonNull(branchProductStockId, "branchProductStockId is required");
        movement.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        movement.productBatchId = productBatchId;
        movement.movementType = Objects.requireNonNull(movementType, "movementType is required");
        movement.enteredQuantity = Objects.requireNonNull(enteredQuantity, "enteredQuantity is required");
        movement.enteredUnitId = Objects.requireNonNull(enteredUnitId, "enteredUnitId is required");
        movement.conversionFactor = Objects.requireNonNull(conversionFactor, "conversionFactor is required");
        movement.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        movement.baseQuantityChange = Objects.requireNonNull(baseQuantityChange, "baseQuantityChange is required");
        movement.quantityBefore = Objects.requireNonNull(quantityBefore, "quantityBefore is required");
        movement.quantityAfter = Objects.requireNonNull(quantityAfter, "quantityAfter is required");
        movement.financialUnitCostSnapshot = Objects.requireNonNull(financialUnitCostSnapshot, "financialUnitCostSnapshot is required");
        movement.inventoryValueChange = Objects.requireNonNull(inventoryValueChange, "inventoryValueChange is required");
        movement.sourceType = Objects.requireNonNull(sourceType, "sourceType is required");
        movement.sourceReferenceId = required(sourceReferenceId, "sourceReferenceId");
        movement.sourceLineReference = optional(sourceLineReference);
        movement.postingKey = required(postingKey, "postingKey");
        movement.postedByActorType = Objects.requireNonNull(actorType, "actorType is required");
        movement.postedByActorId = Objects.requireNonNull(actorId, "actorId is required");
        movement.reason = optional(reason);
        movement.notes = optional(notes);
        movement.postedAt = Objects.requireNonNull(postedAt, "postedAt is required");
        return movement;
    }

    public static StockMovement change(Long businessId, Long branchId, Long branchProductStockId,
                                       Long productVariantId, Long productBatchId,
                                       StockMovementType movementType, BigDecimal enteredQuantity,
                                       Long enteredUnitId, BigDecimal conversionFactor,
                                       Long baseInventoryUnitId, BigDecimal baseQuantityChange,
                                       BigDecimal quantityBefore, BigDecimal quantityAfter,
                                       BigDecimal financialUnitCostSnapshot, BigDecimal inventoryValueChange,
                                       StockSourceType sourceType, String sourceReferenceId,
                                       String sourceLineReference, String postingKey, Long reversalOfMovementId,
                                       InventoryActorType actorType, Long actorId,
                                       String reason, String notes, Instant postedAt) {
        StockMovement movement = new StockMovement();
        movement.businessId = Objects.requireNonNull(businessId, "businessId is required");
        movement.branchId = Objects.requireNonNull(branchId, "branchId is required");
        movement.branchProductStockId = Objects.requireNonNull(branchProductStockId, "branchProductStockId is required");
        movement.productVariantId = Objects.requireNonNull(productVariantId, "productVariantId is required");
        movement.productBatchId = productBatchId;
        movement.movementType = Objects.requireNonNull(movementType, "movementType is required");
        movement.enteredQuantity = Objects.requireNonNull(enteredQuantity, "enteredQuantity is required");
        movement.enteredUnitId = Objects.requireNonNull(enteredUnitId, "enteredUnitId is required");
        movement.conversionFactor = Objects.requireNonNull(conversionFactor, "conversionFactor is required");
        movement.baseInventoryUnitId = Objects.requireNonNull(baseInventoryUnitId, "baseInventoryUnitId is required");
        movement.baseQuantityChange = Objects.requireNonNull(baseQuantityChange, "baseQuantityChange is required");
        movement.quantityBefore = Objects.requireNonNull(quantityBefore, "quantityBefore is required");
        movement.quantityAfter = Objects.requireNonNull(quantityAfter, "quantityAfter is required");
        movement.financialUnitCostSnapshot = Objects.requireNonNull(financialUnitCostSnapshot, "financialUnitCostSnapshot is required");
        movement.inventoryValueChange = Objects.requireNonNull(inventoryValueChange, "inventoryValueChange is required");
        movement.sourceType = Objects.requireNonNull(sourceType, "sourceType is required");
        movement.sourceReferenceId = required(sourceReferenceId, "sourceReferenceId");
        movement.sourceLineReference = optional(sourceLineReference);
        movement.postingKey = required(postingKey, "postingKey");
        movement.reversalOfMovementId = reversalOfMovementId;
        movement.postedByActorType = Objects.requireNonNull(actorType, "actorType is required");
        movement.postedByActorId = Objects.requireNonNull(actorId, "actorId is required");
        movement.reason = optional(reason);
        movement.notes = optional(notes);
        movement.postedAt = Objects.requireNonNull(postedAt, "postedAt is required");
        return movement;
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
