package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.AdjustmentSourceType;
import com.spark.falcon.inventory.entity.AdjustmentType;
import com.spark.falcon.inventory.entity.InventoryOperationStatus;

import java.time.Instant;

public record StockAdjustmentFilter(
        Long adjustmentId,
        Long productVariantId,
        Long batchId,
        AdjustmentType type,
        String reason,
        InventoryOperationStatus status,
        AdjustmentSourceType sourceType,
        Long createdBy,
        Long approvedBy,
        Instant from,
        Instant to
) {
}
