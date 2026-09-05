package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.InventoryOperationStatus;

import java.time.Instant;

public record InventoryLossFilter(
        Long lossId,
        Long productVariantId,
        Long batchId,
        String reason,
        InventoryOperationStatus status,
        Long createdBy,
        Long approvedBy,
        Instant from,
        Instant to
) {
}
