package com.spark.falcon.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PosCartLineRequest {
    @NotNull @Positive
    private Long productVariantId;

    @NotNull @Positive
    private Long unitId;

    @NotNull @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal discount = BigDecimal.ZERO;
}
