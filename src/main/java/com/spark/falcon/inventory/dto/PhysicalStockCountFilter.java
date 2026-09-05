package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.InventoryOperationStatus;
import com.spark.falcon.inventory.entity.StockCountScope;

import java.time.Instant;

public record PhysicalStockCountFilter(
        Long countId,
        StockCountScope scope,
        InventoryOperationStatus status,
        Long createdBy,
        Long assignedCounterId,
        Instant from,
        Instant to
) {
}
