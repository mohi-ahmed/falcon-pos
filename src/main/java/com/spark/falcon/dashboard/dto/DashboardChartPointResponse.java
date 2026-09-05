package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardChartPointResponse(
        LocalDate date,
        BigDecimal netSales,
        BigDecimal operatingExpense
) { }
