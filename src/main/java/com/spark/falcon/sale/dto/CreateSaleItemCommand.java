package com.spark.falcon.sale.dto;

import java.math.BigDecimal;

public record CreateSaleItemCommand(
        Long productVariantId,
        String productName,
        String variantName,
        String productCode,
        BigDecimal enteredQuantity,
        Long enteredUnitId,
        BigDecimal conversionFactor,
        Long baseInventoryUnitId,
        BigDecimal baseQuantity,
        BigDecimal unitPrice,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal taxRate,
        String taxMethod,
        BigDecimal taxAmount,
        BigDecimal linePayable
) {
}
