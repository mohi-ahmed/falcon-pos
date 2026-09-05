package com.spark.falcon.payment.dto;

import com.spark.falcon.payment.entity.PaymentAuditAction;

import java.time.Instant;

public record PaymentAuditResponse(
        Long id,
        PaymentAuditAction action,
        Long actorId,
        String details,
        Instant createdAt
) {
}
