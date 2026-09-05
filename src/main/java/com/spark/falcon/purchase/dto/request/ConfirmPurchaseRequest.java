package com.spark.falcon.purchase.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ConfirmPurchaseRequest {
    @DecimalMin(value = "0.0000")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Positive
    private Long paymentMethodId;

    @Size(max = 160)
    private String transactionReference;

    @Positive
    private Long cashLocationId;

    @Positive
    private Long registerId;

    @Positive
    private Long cashierShiftId;
}
