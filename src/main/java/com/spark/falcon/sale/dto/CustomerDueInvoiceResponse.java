package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SalePaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CustomerDueInvoiceResponse(
        Long id,
        Long customerId,
        Instant createdAt,
        LocalDate dueDate,
        BigDecimal totalPayable,
        BigDecimal paidAmount,
        BigDecimal returnedAmount,
        BigDecimal paymentReversalAmount,
        BigDecimal dueAmount,
        long daysOverdue,
        SalePaymentStatus paymentStatus
) {
}
