package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;

public record SaleInventoryReversalBatchRequest(Long productBatchId, BigDecimal baseQuantity) {
}
