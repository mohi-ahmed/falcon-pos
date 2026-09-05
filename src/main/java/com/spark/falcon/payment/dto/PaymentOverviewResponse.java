package com.spark.falcon.payment.dto;

import java.math.BigDecimal;

public record PaymentOverviewResponse(
        BigDecimal customerDueCollected,
        BigDecimal supplierDuePaid,
        BigDecimal cashReceipts,
        BigDecimal cashOutflows,
        BigDecimal cardCollections,
        BigDecimal mobileBankingCollections,
        BigDecimal bankCollections,
        long failedPayments,
        long reversedPayments,
        BigDecimal unallocatedCustomerCredit,
        long pendingPayments
) {
}
