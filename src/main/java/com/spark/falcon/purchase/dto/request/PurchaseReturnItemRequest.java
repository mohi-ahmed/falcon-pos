package com.spark.falcon.purchase.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PurchaseReturnItemRequest {
    @NotNull @Positive
    private Long purchaseItemId;

    @NotNull @Positive
    private Long returnUnitId;

    @NotNull @DecimalMin(value = "0.00000001")
    private BigDecimal returnQuantity;
}
