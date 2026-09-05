package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSalesSummaryResponse(
        long confirmedInvoices,
        BigDecimal totalItemsSold,
        BigDecimal averageInvoiceValue,
        long heldSales,
        long confirmedSalesReturns,
        long voidedOrReversedTransactions,
        BigDecimal todayNetSales,
        BigDecimal yesterdayNetSales,
        BigDecimal todayVsYesterdayPercent
) { }
