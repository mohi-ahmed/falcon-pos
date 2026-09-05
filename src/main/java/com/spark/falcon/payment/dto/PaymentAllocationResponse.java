package com.spark.falcon.payment.dto;

import com.spark.falcon.payment.entity.PaymentAllocationEffect;
import com.spark.falcon.payment.entity.PaymentInvoiceType;

import java.math.BigDecimal;

public record PaymentAllocationResponse(
        Long id,
        PaymentInvoiceType invoiceType,
        Long invoiceId,
        PaymentAllocationEffect effect,
        BigDecimal amount,
        BigDecimal dueBefore,
        BigDecimal dueAfter
) {
}
