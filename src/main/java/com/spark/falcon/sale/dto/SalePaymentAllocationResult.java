package com.spark.falcon.sale.dto;

import com.spark.falcon.sale.entity.SalePaymentStatus;

import java.math.BigDecimal;

public record SalePaymentAllocationResult(
        Long saleId,
        BigDecimal amount,
        BigDecimal dueBefore,
        BigDecimal dueAfter,
        SalePaymentStatus paymentStatus
) {
}
