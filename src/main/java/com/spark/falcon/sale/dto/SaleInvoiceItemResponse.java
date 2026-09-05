package com.spark.falcon.sale.dto;

import java.math.BigDecimal;

public record SaleInvoiceItemResponse(
        String productName,
        String variantName,
        String productCode,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal linePayable
) {
}
