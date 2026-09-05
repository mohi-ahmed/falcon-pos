package com.spark.falcon.dashboard.dto;

import java.time.LocalDate;
import java.time.YearMonth;

public record DashboardDateRangeResponse(
        DashboardPeriod period,
        LocalDate from,
        LocalDate to,
        YearMonth selectedMonth,
        String label
) { }
