package com.spark.falcon.cashmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CashTransferReversalRequest {

    @NotBlank
    @Size(max = 1000)
    private String reason;

    @NotBlank
    @Size(max = 100)
    private String reversalKey;
}
