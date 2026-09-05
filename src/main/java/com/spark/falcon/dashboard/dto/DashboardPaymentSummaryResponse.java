package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;

public record DashboardPaymentSummaryResponse(
        BigDecimal customerDueCollected,
        BigDecimal supplierDuePaid,
        BigDecimal cashReceipts,
        BigDecimal cashOutflows,
        BigDecimal cardCollections,
        BigDecimal mobileBankingCollections,
        BigDecimal bankCollections,
        long failedPayments,
        long reversedPayments,
        long pendingPayments
) { }
