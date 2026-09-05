package com.spark.falcon.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SaleBatchAllocationCommand(
        Long productBatchId,
        String batchNumber,
        LocalDate expiryDate,
        BigDecimal baseQuantity
) {
}
