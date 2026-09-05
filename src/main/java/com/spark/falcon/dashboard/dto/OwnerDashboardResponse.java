package com.spark.falcon.dashboard.dto;

import com.spark.falcon.analytics.dto.FinancialSummaryResponse;
import com.spark.falcon.analytics.dto.RankedMetricResponse;
import com.spark.falcon.analytics.dto.PaymentMixResponse;

import java.time.Instant;
import java.util.List;

public record OwnerDashboardResponse(
        DashboardContextResponse context,
        DashboardDateRangeResponse reportingPeriod,
        DashboardAccessResponse access,
        FinancialSummaryResponse financial,
        DashboardSalesSummaryResponse sales,
        DashboardInventorySummaryResponse inventory,
        DashboardPurchaseSummaryResponse purchase,
        DashboardPaymentSummaryResponse payment,
        DashboardCustomerSummaryResponse customer,
        DashboardCashSummaryResponse cash,
        List<DashboardChartPointResponse> chart,
        List<RankedMetricResponse> topProducts,
        List<RankedMetricResponse> topCustomers,
        List<RankedMetricResponse> leadingSuppliers,
        List<PaymentMixResponse> paymentMix,
        List<DashboardAlertResponse> alerts,
        List<DashboardQuickAccessResponse> quickAccess,
        Instant refreshedAt
) { }
