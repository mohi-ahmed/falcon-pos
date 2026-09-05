package com.spark.falcon.pos.dto;

import java.math.BigDecimal;

public record PosPreparedLine(
        Long productVariantId,
        String productName,
        String variantName,
        String productCode,
        String thumbnailReference,
        Long unitId,
        BigDecimal enteredQuantity,
        BigDecimal conversionFactor,
        Long baseInventoryUnitId,
        BigDecimal baseQuantity,
        BigDecimal sellableBaseQuantity,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        String taxMethod,
        BigDecimal taxAmount,
        BigDecimal linePayable,
        boolean batchControlled
) {
}
