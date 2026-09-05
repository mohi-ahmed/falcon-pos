package com.spark.falcon.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaymentReversalRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;
}
