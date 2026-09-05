package com.spark.falcon.product.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateProductUnitConversionCommand(
        Long ownerId,
        Long productId,
        Long variantId,
        Long sourceUnitId,
        Long targetUnitId,
        BigDecimal conversionFactor,
        int decimalPrecision,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
