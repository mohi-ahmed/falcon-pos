package com.spark.falcon.payment.dto;

import java.math.BigDecimal;

public record SupplierPaymentAllocationCommand(
        Long purchaseId,
        BigDecimal amount
) {
}
