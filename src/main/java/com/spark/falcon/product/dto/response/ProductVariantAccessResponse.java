package com.spark.falcon.product.dto.response;

import java.math.BigDecimal;

public record ProductVariantAccessResponse(
        Long businessId,
        Long branchId,
        Long productId,
        Long variantId,
        String productName,
        String referenceCode,
        String variantName,
        String sku,
        Long baseInventoryUnitId,
        Long purchaseUnitId,
        Long sellingUnitId,
        BigDecimal sellingPrice,
        String thumbnailReference,
        Long taxRateId,
        String taxCalculationMethod,
        boolean trackExpiry,
        boolean batchTrackingRequired
) {
}