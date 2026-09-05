package com.spark.falcon.purchase.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class SupplierPaymentAllocationRequest {
    @NotNull @Positive
    private Long purchaseId;

    @NotNull @DecimalMin(value = "0.0001")
    private BigDecimal amount;
}
