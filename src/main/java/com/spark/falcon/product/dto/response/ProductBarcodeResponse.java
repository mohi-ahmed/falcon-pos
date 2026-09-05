package com.spark.falcon.product.dto.response;

public record ProductBarcodeResponse(
        Long id,
        Long businessId,
        Long productVariantId,
        Long unitId,
        String barcode,
        boolean active) {
}
