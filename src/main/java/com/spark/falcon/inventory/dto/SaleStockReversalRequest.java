package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaleStockReversalRequest(
        Long businessId, Long branchId, Long saleId, Long saleItemId, Long originalMovementId,
        Long productVariantId, Long baseInventoryUnitId, BigDecimal baseQuantity,
        BigDecimal preservedFinancialCostSnapshot, List<SaleInventoryReversalBatchRequest> batches,
        String postingKey, Long actorId, String reason, String notes) {
}
