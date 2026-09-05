package com.spark.falcon.purchase.dto.response;

import java.math.BigDecimal;

public record PurchasePaymentAllocationResult(
        Long purchaseId,
        BigDecimal amount,
        BigDecimal dueBefore,
        BigDecimal dueAfter
) {
}
