package com.spark.falcon.payment.dto;

import java.math.BigDecimal;

public record CustomerRefundCommand(
        Long businessId,
        Long branchId,
        Long actorId,
        Long customerId,
        Long saleId,
        Long saleReturnId,
        BigDecimal amount,
        Long paymentMethodId,
        String transactionReference,
        String accountReference,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        String idempotencyKey,
        String notes
) {
}
