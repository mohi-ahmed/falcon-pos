package com.spark.falcon.purchase.dto.command;

import java.math.BigDecimal;

public record ConfirmPurchaseCommand(
        Long ownerId,
        Long purchaseId,
        BigDecimal paidAmount,
        Long paymentMethodId,
        String transactionReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId) {
}
