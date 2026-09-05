package com.spark.falcon.product.dto.response;

public record ProductBarcodeAccessResponse(
        ProductVariantAccessResponse variant,
        Long barcodeId,
        String barcode,
        Long unitId) {
}
