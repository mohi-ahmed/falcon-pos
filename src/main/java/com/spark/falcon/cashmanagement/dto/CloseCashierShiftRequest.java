package com.spark.falcon.cashmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CloseCashierShiftRequest {

    @NotNull
    @DecimalMin(value = "0.0000")
    private BigDecimal physicalCountedCash;

    @NotNull
    private Long closingCashLocationId;

    @Size(max = 2000)
    private String denominationCount;

    @Size(max = 1000)
    private String note;

    private boolean varianceApproved;

    @NotBlank
    @Size(max = 100)
    private String closeKey;
}
