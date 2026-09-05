package com.spark.falcon.sale.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record SaleInvoicePaymentLine(
        Long paymentId,
        String paymentMethodName,
        BigDecimal amount,
        String transactionReference,
        ZonedDateTime confirmedAt
) {
}
