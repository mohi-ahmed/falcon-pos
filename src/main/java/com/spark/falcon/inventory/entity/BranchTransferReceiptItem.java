package com.spark.falcon.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "branch_transfer_receipt_items", indexes = {
        @Index(name = "idx_transfer_receipt_item_transfer_item", columnList = "transfer_item_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchTransferReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_transfer_receipt_item_receipt"))
    @Getter(AccessLevel.NONE)
    private BranchTransferReceipt receipt;

    @Column(name = "transfer_item_id", nullable = false, updatable = false)
    private Long transferItemId;

    @Column(name = "accepted_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal acceptedQuantity;

    @Column(name = "damaged_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal damagedQuantity;

    @Column(name = "missing_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal missingQuantity;

    @Column(name = "rejected_quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal rejectedQuantity;

    @Column(name = "discrepancy_reason", length = 500)
    private String discrepancyReason;

    @Column(name = "destination_batch_id")
    private Long destinationBatchId;

    @Column(name = "stock_movement_id")
    private Long stockMovementId;

    public static BranchTransferReceiptItem create(Long transferItemId,
                                                   BigDecimal acceptedQuantity,
                                                   BigDecimal damagedQuantity,
                                                   BigDecimal missingQuantity,
                                                   BigDecimal rejectedQuantity,
                                                   String discrepancyReason,
                                                   Long destinationBatchId,
                                                   Long stockMovementId) {
        BranchTransferReceiptItem item = new BranchTransferReceiptItem();
        item.transferItemId = transferItemId;
        item.acceptedQuantity = nz(acceptedQuantity);
        item.damagedQuantity = nz(damagedQuantity);
        item.missingQuantity = nz(missingQuantity);
        item.rejectedQuantity = nz(rejectedQuantity);
        item.discrepancyReason = optional(discrepancyReason);
        item.destinationBatchId = destinationBatchId;
        item.stockMovementId = stockMovementId;
        return item;
    }

    void attach(BranchTransferReceipt receipt) {
        this.receipt = receipt;
    }

    private static BigDecimal nz(BigDecimal value) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value).setScale(8, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) throw new IllegalArgumentException("Receipt quantities must not be negative");
        return normalized;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
