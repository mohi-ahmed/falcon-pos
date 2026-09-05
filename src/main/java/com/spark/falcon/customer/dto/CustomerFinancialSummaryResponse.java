package com.spark.falcon.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerFinancialSummaryResponse(
        Long customerId,
        BigDecimal totalConfirmedPurchases,
        BigDecimal totalSalesReturns,
        BigDecimal totalPaid,
        BigDecimal outstandingDue,
        BigDecimal overdueAmount,
        BigDecimal customerCredit,
        Instant lastPurchaseAt
) {
}
