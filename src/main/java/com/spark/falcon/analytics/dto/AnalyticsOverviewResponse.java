package com.spark.falcon.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsOverviewResponse(
        LocalDate from,
        LocalDate to,
        FinancialSummaryResponse financial,
        ExpirySummaryResponse expiry,
        List<RankedMetricResponse> topProducts,
        List<RankedMetricResponse> topCustomers,
        List<RankedMetricResponse> leadingSuppliers,
        List<RankedMetricResponse> topBrands,
        List<CustomerBirthdayResponse> birthdays,
        List<DailyPerformanceResponse> dailyPerformance,
        List<PaymentMixResponse> paymentMix,
        List<LoginLogResponse> loginLogs
) { }
