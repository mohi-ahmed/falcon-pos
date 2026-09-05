package com.spark.falcon.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerPaymentAllocationRequest {
    @NotNull
    private Long saleId;

    @NotNull
    @DecimalMin(value = "0.0001")
    private BigDecimal amount;
}
