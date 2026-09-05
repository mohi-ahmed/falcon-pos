package com.spark.falcon.purchase.dto.command;

import java.math.BigDecimal;

public record SupplierPaymentAllocationCommand(
        Long purchaseId,
        BigDecimal amount) {
}
