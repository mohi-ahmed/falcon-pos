package com.spark.falcon.supplier.dto;

import java.math.BigDecimal;

public record SupplierExpiryPerformanceResponse(
        long activeExpiryControlledBatchesSupplied,
        long nearExpiryBatchCount,
        long expiredBatchCount,
        long expiryRelatedPurchaseReturnCount,
        BigDecimal supplierRefundsAndCredits,
        BigDecimal expiryLossAmount
) {
}
