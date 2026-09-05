package com.spark.falcon.cashmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class OpenCashierShiftRequest {
    @NotNull
    @Positive
    private Long registerId;

    @NotNull
    @Positive
    private Long cashierUserId;

    @NotNull
    @Positive
    private Long sourceCashLocationId;

    @NotBlank
    @Size(max = 80)
    private String shiftCode;

    @NotBlank
    @Size(max = 100)
    private String openKey;

    @NotNull
    @DecimalMin(value = "0.0000")
    private BigDecimal openingFloat;

    @Size(max = 1000)
    private String note;
}
