package com.spark.falcon.product.dto.response;

import com.spark.falcon.product.entity.enumtype.ProductStatus;

import java.math.BigDecimal;

public record ProductVariantResponse(
        Long id,
        Long productId,
        String variantName,
        String sku,
        Long baseInventoryUnitId,
        Long purchaseUnitId,
        Long sellingUnitId,
        BigDecimal sellingPrice,
        BigDecimal reorderLevel,
        ProductStatus status) {
}
