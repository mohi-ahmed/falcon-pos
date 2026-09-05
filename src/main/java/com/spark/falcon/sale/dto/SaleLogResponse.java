package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SaleAuditAction;

import java.math.BigDecimal;
import java.time.Instant;

public record SaleLogResponse(
        Long auditId,
        Instant createdAt,
        SaleAuditAction transactionType,
        Long saleId,
        Long customerId,
        String customerName,
        Long actorId,
        BigDecimal amount,
        String paymentMethod,
        String details
) {
}
