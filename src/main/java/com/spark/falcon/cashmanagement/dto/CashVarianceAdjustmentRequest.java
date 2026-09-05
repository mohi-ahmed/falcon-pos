package com.spark.falcon.cashmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CashVarianceAdjustmentRequest {

    @NotBlank(message = "Resolution note is required")
    @Size(max = 1000, message = "Resolution note must not exceed 1000 characters")
    private String note;
}
