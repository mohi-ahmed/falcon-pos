package com.spark.falcon.payment.dto;

import com.spark.falcon.payment.entity.PaymentDirection;
import com.spark.falcon.payment.entity.PaymentStatus;

import java.time.Instant;

public record PaymentHistoryFilter(
        Long branchId,
        Long paymentId,
        Long invoiceId,
        Long customerId,
        Long supplierId,
        Long paymentMethodId,
        PaymentDirection direction,
        PaymentStatus status,
        Long actorId,
        Instant from,
        Instant to
) {
}
