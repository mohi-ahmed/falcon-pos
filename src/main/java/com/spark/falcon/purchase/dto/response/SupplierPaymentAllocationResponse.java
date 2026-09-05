package com.spark.falcon.purchase.dto.response;

import java.math.BigDecimal;

public record SupplierPaymentAllocationResponse(
        Long id,
        Long purchaseId,
        BigDecimal amount,
        BigDecimal dueBefore,
        BigDecimal dueAfter) {
}
