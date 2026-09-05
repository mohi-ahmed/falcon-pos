package com.spark.falcon.purchase.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class PurchaseItemRequest {
    @NotNull @Positive
    private Long productVariantId;

    @NotNull @Positive
    private Long enteredUnitId;

    @NotNull @DecimalMin(value = "0.00000001")
    private BigDecimal enteredQuantity;

    @NotNull @DecimalMin(value = "0.0000")
    private BigDecimal unitCost;

    @DecimalMin(value = "0.0000")
    private BigDecimal intendedSellingPrice;

    @DecimalMin(value = "0.0000")
    private BigDecimal itemTax = BigDecimal.ZERO;

    @DecimalMin(value = "0.0000")
    private BigDecimal itemDiscount = BigDecimal.ZERO;

    @Size(max = 100)
    private String batchNumber;

    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
}
