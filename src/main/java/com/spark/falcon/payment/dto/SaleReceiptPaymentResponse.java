package com.spark.falcon.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SaleReceiptPaymentResponse(
        Long paymentId,
        String paymentMethodName,
        BigDecimal amount,
        String transactionReference,
        Instant confirmedAt
) {
}
