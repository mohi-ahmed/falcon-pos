package com.spark.falcon.product.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class ProductUnitConversionRequest {
    @NotNull @Positive
    private Long sourceUnitId;

    @NotNull @Positive
    private Long targetUnitId;

    @NotNull @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal conversionFactor;

    @Min(0)
    private int decimalPrecision;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveTo;
}
