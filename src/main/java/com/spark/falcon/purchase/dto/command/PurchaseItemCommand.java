package com.spark.falcon.purchase.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PurchaseItemCommand(
        Long productVariantId,
        Long enteredUnitId,
        BigDecimal enteredQuantity,
        BigDecimal unitCost,
        BigDecimal intendedSellingPrice,
        BigDecimal itemTax,
        BigDecimal itemDiscount,
        String batchNumber,
        LocalDate manufacturingDate,
        LocalDate expiryDate) {
}
