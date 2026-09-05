package com.spark.falcon.product.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductUnitConversionResponse(
        Long id,
        Long productVariantId,
        Long sourceUnitId,
        Long targetUnitId,
        BigDecimal conversionFactor,
        int decimalPrecision,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active) {
}
