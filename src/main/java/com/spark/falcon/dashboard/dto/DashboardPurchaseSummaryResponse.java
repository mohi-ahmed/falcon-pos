package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;

public record DashboardPurchaseSummaryResponse(
        BigDecimal todayConfirmedPurchases,
        BigDecimal periodConfirmedPurchases,
        BigDecimal supplierOutstandingDue,
        long duePurchaseInvoices,
        long pendingPurchaseReturns,
        long pendingSupplierRefundOrCredits
) { }
