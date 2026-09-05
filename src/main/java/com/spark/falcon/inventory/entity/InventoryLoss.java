package com.spark.falcon.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_losses", uniqueConstraints =
        @UniqueConstraint(name = "uk_inventory_loss_business_key", columnNames = {"business_id", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class InventoryLoss {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "business_id", nullable = false) private Long businessId;
    @Column(name = "branch_id", nullable = false) private Long branchId;
    @Column(name = "product_variant_id", nullable = false) private Long productVariantId;
    @Column(name = "product_batch_id") private Long productBatchId;
    @Column(name = "disposal_date", nullable = false) private LocalDate disposalDate;
    @Column(name = "entered_quantity", nullable = false, precision = 19, scale = 8) private BigDecimal enteredQuantity;
    @Column(name = "entered_unit_id", nullable = false) private Long enteredUnitId;
    @Column(name = "conversion_factor", nullable = false, precision = 19, scale = 8) private BigDecimal conversionFactor;
    @Column(name = "base_quantity", nullable = false, precision = 19, scale = 8) private BigDecimal baseQuantity;
    @Column(name = "quantity_before", nullable = false, precision = 19, scale = 8) private BigDecimal quantityBefore;
    @Column(name = "quantity_after", nullable = false, precision = 19, scale = 8) private BigDecimal quantityAfter;
    @Column(name = "weighted_average_cost_snapshot", nullable = false, precision = 19, scale = 4) private BigDecimal weightedAverageCostSnapshot;
    @Column(name = "financial_loss", nullable = false, precision = 19, scale = 4) private BigDecimal financialLoss;
    @Column(nullable = false, length = 80) private String reason;
    @Column(length = 1000) private String notes;
    @Column(name = "attachment_reference", length = 500) private String attachmentReference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InventoryOperationStatus status;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "approved_by") private Long approvedBy;
    @Column(name = "posted_by") private Long postedBy;
    @Column(name = "stock_movement_id", unique = true) private Long stockMovementId;
    @Column(name = "reversal_movement_id", unique = true) private Long reversalMovementId;
    @Enumerated(EnumType.STRING) @Column(name = "previous_batch_status", length = 24) private ProductBatchStatus previousBatchStatus;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "posted_at") private Instant postedAt;

    public static InventoryLoss draft(Long businessId, Long branchId, Long variantId, Long batchId, LocalDate date,
                                      BigDecimal entered, Long unit, BigDecimal factor, BigDecimal base,
                                      BigDecimal quantityBefore, BigDecimal cost, String reason, String notes,
                                      String attachment, ProductBatchStatus previousStatus, Long actor,
                                      String key, Instant now) {
        InventoryLoss l = new InventoryLoss();
        l.businessId = businessId; l.branchId = branchId; l.productVariantId = variantId; l.productBatchId = batchId;
        l.disposalDate = date; l.enteredQuantity = entered; l.enteredUnitId = unit; l.conversionFactor = factor;
        l.baseQuantity = base; l.reason = reason; l.notes = notes; l.attachmentReference = attachment;
        l.previousBatchStatus = previousStatus; l.createdBy = actor; l.idempotencyKey = key; l.createdAt = now;
        l.status = InventoryOperationStatus.DRAFT; l.recalculate(quantityBefore, cost);
        return l;
    }

    public void updateDraft(Long variantId, Long batchId, LocalDate date, BigDecimal entered, Long unit,
                            BigDecimal factor, BigDecimal base, BigDecimal quantityBefore, BigDecimal cost,
                            String reason, String notes, String attachment, ProductBatchStatus previousStatus) {
        require(InventoryOperationStatus.DRAFT);
        productVariantId = variantId; productBatchId = batchId; disposalDate = date; enteredQuantity = entered;
        enteredUnitId = unit; conversionFactor = factor; baseQuantity = base; this.reason = reason;
        this.notes = notes; attachmentReference = attachment; previousBatchStatus = previousStatus;
        recalculate(quantityBefore, cost);
    }

    public void submit() { require(InventoryOperationStatus.DRAFT); status = InventoryOperationStatus.SUBMITTED; }
    public void approve(Long actor) { require(InventoryOperationStatus.SUBMITTED); approvedBy = actor; status = InventoryOperationStatus.APPROVED; }
    public void reject() { require(InventoryOperationStatus.SUBMITTED); status = InventoryOperationStatus.REJECTED; }
    public void cancel() {
        if (status != InventoryOperationStatus.DRAFT && status != InventoryOperationStatus.SUBMITTED) throw new IllegalStateException("Loss cannot be cancelled");
        status = InventoryOperationStatus.CANCELLED;
    }
    public void posted(Long actor, Long movement, Instant now) { require(InventoryOperationStatus.APPROVED); postedBy = actor; stockMovementId = movement; postedAt = now; status = InventoryOperationStatus.POSTED; }
    public void reversed(Long movement) { require(InventoryOperationStatus.POSTED); reversalMovementId = movement; status = InventoryOperationStatus.REVERSED; }
    public void refreshPostingSnapshot(BigDecimal before, BigDecimal cost, ProductBatchStatus previousStatus) {
        require(InventoryOperationStatus.APPROVED); previousBatchStatus = previousStatus; recalculate(before, cost);
    }

    private void recalculate(BigDecimal before, BigDecimal cost) {
        quantityBefore = before; quantityAfter = before.subtract(baseQuantity);
        weightedAverageCostSnapshot = cost;
        financialLoss = baseQuantity.multiply(cost).setScale(4, RoundingMode.HALF_UP);
    }
    private void require(InventoryOperationStatus expected) { if (status != expected) throw new IllegalStateException("Loss must be " + expected); }
}
