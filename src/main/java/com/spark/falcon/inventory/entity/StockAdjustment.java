package com.spark.falcon.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "stock_adjustments", uniqueConstraints =
        @UniqueConstraint(name = "uk_adjustment_business_key", columnNames = {"business_id", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class StockAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "business_id", nullable = false) private Long businessId;
    @Column(name = "branch_id", nullable = false) private Long branchId;
    @Column(name = "product_variant_id", nullable = false) private Long productVariantId;
    @Column(name = "product_batch_id") private Long productBatchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private AdjustmentType type;
    @Column(name = "entered_quantity", nullable = false, precision = 19, scale = 8) private BigDecimal enteredQuantity;
    @Column(name = "entered_unit_id", nullable = false) private Long enteredUnitId;
    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 8) private BigDecimal conversionFactor;
    @Column(name = "base_quantity", nullable = false, precision = 19, scale = 8) private BigDecimal baseQuantity;
    @Column(name = "quantity_before", nullable = false, precision = 19, scale = 8) private BigDecimal quantityBefore;
    @Column(name = "quantity_after", nullable = false, precision = 19, scale = 8) private BigDecimal quantityAfter;
    @Column(name = "unit_cost_snapshot", nullable = false, precision = 19, scale = 4) private BigDecimal unitCostSnapshot;
    @Column(name = "inventory_value_change", nullable = false, precision = 19, scale = 4) private BigDecimal inventoryValueChange;
    @Column(nullable = false, length = 240) private String reason;
    @Column(length = 1000) private String notes;
    @Column(name = "attachment_reference", length = 500) private String attachmentReference;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 20) private AdjustmentSourceType sourceType;
    @Column(name = "source_count_id") private Long sourceCountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InventoryOperationStatus status;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "approved_by") private Long approvedBy;
    @Column(name = "posted_by") private Long postedBy;
    @Column(name = "stock_movement_id", unique = true) private Long stockMovementId;
    @Column(name = "reversal_movement_id", unique = true) private Long reversalMovementId;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "posted_at") private Instant postedAt;

    public static StockAdjustment draft(Long businessId, Long branchId, Long variantId, Long batchId,
                                        AdjustmentType type, BigDecimal enteredQuantity, Long enteredUnitId,
                                        BigDecimal factor, BigDecimal baseQuantity, BigDecimal quantityBefore,
                                        BigDecimal cost, String reason, String notes, String attachment,
                                        AdjustmentSourceType sourceType, Long sourceCountId, Long actorId,
                                        String idempotencyKey, Instant now) {
        StockAdjustment a = new StockAdjustment();
        a.businessId = businessId; a.branchId = branchId; a.productVariantId = variantId; a.productBatchId = batchId;
        a.type = type; a.enteredQuantity = enteredQuantity; a.enteredUnitId = enteredUnitId;
        a.conversionFactor = factor; a.baseQuantity = baseQuantity; a.quantityBefore = quantityBefore;
        a.unitCostSnapshot = cost; a.reason = reason; a.notes = notes; a.attachmentReference = attachment;
        a.sourceType = sourceType; a.sourceCountId = sourceCountId; a.createdBy = actorId;
        a.idempotencyKey = idempotencyKey; a.createdAt = now; a.status = InventoryOperationStatus.DRAFT;
        a.recalculateSnapshot(quantityBefore, cost);
        return a;
    }

    public void updateDraft(Long variantId, Long batchId, AdjustmentType type, BigDecimal enteredQuantity,
                            Long enteredUnitId, BigDecimal factor, BigDecimal baseQuantity, BigDecimal quantityBefore,
                            BigDecimal cost, String reason, String notes, String attachment,
                            AdjustmentSourceType sourceType, Long sourceCountId) {
        require(InventoryOperationStatus.DRAFT);
        this.productVariantId = variantId; this.productBatchId = batchId; this.type = type;
        this.enteredQuantity = enteredQuantity; this.enteredUnitId = enteredUnitId;
        this.conversionFactor = factor; this.baseQuantity = baseQuantity; this.reason = reason;
        this.notes = notes; this.attachmentReference = attachment; this.sourceType = sourceType;
        this.sourceCountId = sourceCountId;
        recalculateSnapshot(quantityBefore, cost);
    }

    public void submit() { require(InventoryOperationStatus.DRAFT); status = InventoryOperationStatus.SUBMITTED; }
    public void approve(Long actor) { require(InventoryOperationStatus.SUBMITTED); approvedBy = actor; status = InventoryOperationStatus.APPROVED; }
    public void reject() { require(InventoryOperationStatus.SUBMITTED); status = InventoryOperationStatus.REJECTED; }
    public void cancel() {
        if (status != InventoryOperationStatus.DRAFT && status != InventoryOperationStatus.SUBMITTED && status != InventoryOperationStatus.APPROVED) {
            throw new IllegalStateException("Adjustment cannot be cancelled");
        }
        status = InventoryOperationStatus.CANCELLED;
    }
    public void posted(Long actor, Long movement, Instant now) {
        require(InventoryOperationStatus.APPROVED); postedBy = actor; stockMovementId = movement; postedAt = now; status = InventoryOperationStatus.POSTED;
    }
    public void reversed(Long movement) { require(InventoryOperationStatus.POSTED); reversalMovementId = movement; status = InventoryOperationStatus.REVERSED; }
    public void refreshPostingSnapshot(BigDecimal before, BigDecimal cost) { require(InventoryOperationStatus.APPROVED); recalculateSnapshot(before, cost); }

    private void recalculateSnapshot(BigDecimal before, BigDecimal cost) {
        quantityBefore = before;
        quantityAfter = type == AdjustmentType.INCREASE ? before.add(baseQuantity) : before.subtract(baseQuantity);
        unitCostSnapshot = cost;
        inventoryValueChange = baseQuantity.multiply(cost).setScale(4, RoundingMode.HALF_UP)
                .multiply(type == AdjustmentType.INCREASE ? BigDecimal.ONE : BigDecimal.ONE.negate());
    }
    private void require(InventoryOperationStatus expected) {
        if (status != expected) throw new IllegalStateException("Adjustment must be " + expected);
    }
}
