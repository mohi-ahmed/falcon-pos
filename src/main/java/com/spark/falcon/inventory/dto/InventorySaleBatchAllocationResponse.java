package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventorySaleBatchAllocationResponse(
        Long productBatchId, String batchNumber, LocalDate expiryDate, BigDecimal baseQuantity) {
}
