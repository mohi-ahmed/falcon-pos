package com.spark.falcon.product.dto.command;

import com.spark.falcon.product.entity.enumtype.ProductStatus;

import java.math.BigDecimal;

public record UpdateProductVariantCommand(
        Long ownerId,
        Long productId,
        Long variantId,
        String variantName,
        String sku,
        Long baseInventoryUnitId,
        Long purchaseUnitId,
        Long sellingUnitId,
        BigDecimal sellingPrice,
        BigDecimal reorderLevel,
        ProductStatus status) {
}
