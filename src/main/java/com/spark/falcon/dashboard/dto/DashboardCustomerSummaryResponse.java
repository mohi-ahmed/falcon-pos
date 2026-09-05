package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;

public record DashboardCustomerSummaryResponse(
        BigDecimal outstandingDue,
        BigDecimal receivedDuePayments,
        BigDecimal overdueAmount,
        long overdueInvoiceCount
) { }
