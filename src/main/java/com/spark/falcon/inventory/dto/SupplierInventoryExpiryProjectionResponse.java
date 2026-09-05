package com.spark.falcon.inventory.dto;

import java.math.BigDecimal;
import java.util.Set;

public record SupplierInventoryExpiryProjectionResponse(
        Set<Long> expiryControlledBatchIds,
        long activeExpiryControlledBatchCount,
        long nearExpiryBatchCount,
        long expiredBatchCount,
        BigDecimal expiryLossAmount
) {
}
