package com.spark.falcon.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerSaleFinancialSummaryResponse(
        BigDecimal totalConfirmedPurchases,
        BigDecimal totalSalesReturns,
        BigDecimal totalPaid,
        BigDecimal outstandingDue,
        BigDecimal overdueAmount,
        BigDecimal customerCredit,
        Instant lastPurchaseAt
) {
}
