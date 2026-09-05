package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.StockMovementType;
import com.spark.falcon.inventory.entity.StockSourceType;

import java.time.Instant;

public record StockMovementFilter(
        Long movementId,
        Long productId,
        Long productVariantId,
        Long batchId,
        StockMovementType movementType,
        StockSourceType sourceType,
        Long userId,
        Instant from,
        Instant to,
        Boolean reversal,
        Long reversalReferenceId,
        String query
) {
}
