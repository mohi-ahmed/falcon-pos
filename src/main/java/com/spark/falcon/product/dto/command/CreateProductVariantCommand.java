package com.spark.falcon.product.dto.command;

import com.spark.falcon.product.entity.enumtype.ProductStatus;

import java.math.BigDecimal;

public record CreateProductVariantCommand(
        Long ownerId,
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
