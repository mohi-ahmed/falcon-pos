package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.AdjustmentSourceType;
import com.spark.falcon.inventory.entity.AdjustmentType;

import java.math.BigDecimal;

public record UpdateStockAdjustmentCommand(
        Long ownerId,
        Long adjustmentId,
        Long productVariantId,
        Long productBatchId,
        AdjustmentType type,
        BigDecimal enteredQuantity,
        Long enteredUnitId,
        String reason,
        String notes,
        String attachmentReference,
        AdjustmentSourceType sourceType,
        Long sourceCountId
) {
}
