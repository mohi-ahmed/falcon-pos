package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SalePaymentStatus;
import com.spark.falcon.sale.entity.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SalePaymentInvoiceResponse(
        Long saleId,
        Long customerId,
        BigDecimal outstandingDue,
        SaleStatus status,
        SalePaymentStatus paymentStatus,
        LocalDate agingDate,
        Instant createdAt
) {
}
