package com.spark.falcon.inventory.dto;

public record SaleReturnStockReversalRequest(
        Long businessId, Long branchId, Long saleReturnId, Long saleReturnItemId,
        Long originalMovementId, String postingKey, Long actorId, String reason, String notes) {
}
