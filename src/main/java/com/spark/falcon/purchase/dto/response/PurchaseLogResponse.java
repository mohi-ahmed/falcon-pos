package com.spark.falcon.purchase.dto.response;

import com.spark.falcon.purchase.entity.enumtype.PurchaseAuditAction;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseLogResponse(
        Long auditId,
        Instant createdAt,
        PurchaseAuditAction transactionType,
        Long purchaseId,
        Long supplierId,
        Long actorId,
        BigDecimal totalAmount,
        String paymentMethod,
        String subjectType,
        Long subjectId,
        String details) {
}
