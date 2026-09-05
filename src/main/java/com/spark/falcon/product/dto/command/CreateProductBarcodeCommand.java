package com.spark.falcon.product.dto.command;

public record CreateProductBarcodeCommand(
        Long ownerId,
        Long productId,
        Long variantId,
        Long unitId,
        String barcode) {
}
