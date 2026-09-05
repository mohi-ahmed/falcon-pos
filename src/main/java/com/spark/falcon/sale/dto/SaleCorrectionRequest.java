package com.spark.falcon.sale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SaleCorrectionRequest {
    @NotBlank
    @Size(max = 500)
    private String reason;

    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
