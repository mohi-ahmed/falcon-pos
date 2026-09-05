package com.spark.falcon.purchase.dto.response;

import java.math.BigDecimal;

public record PurchasePaymentInvoiceResponse(
        Long purchaseId,
        BigDecimal outstandingDue
) {
}
