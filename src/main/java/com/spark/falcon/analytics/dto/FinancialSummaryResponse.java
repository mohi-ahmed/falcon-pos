package com.spark.falcon.analytics.dto;

import java.math.BigDecimal;

public record FinancialSummaryResponse(
        BigDecimal openingBalance,
        BigDecimal todayIncome,
        BigDecimal totalIncome,
        BigDecimal todayOperatingExpense,
        BigDecimal availableCashBalance,
        BigDecimal closingBalance,
        BigDecimal grossSales,
        BigDecimal salesReturns,
        BigDecimal discounts,
        BigDecimal netSales,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal operatingExpense,
        BigDecimal inventoryLoss,
        BigDecimal netProfit,
        BigDecimal customerDue,
        BigDecimal supplierDue,
        BigDecimal receivedPayments
) { }
