package com.spark.falcon.pos.dto;

import java.math.BigDecimal;

public record PosProductResponse(
        Long productId,
        Long productVariantId,
        String productName,
        String variantName,
        String productCode,
        String sku,
        String thumbnailReference,
        Long sellingUnitId,
        BigDecimal sellingPrice,
        BigDecimal sellableBaseQuantity,
        Long baseInventoryUnitId,
        boolean batchControlled
) {
}
