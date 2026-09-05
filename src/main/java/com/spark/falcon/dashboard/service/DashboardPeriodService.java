package com.spark.falcon.dashboard.service;

import com.spark.falcon.dashboard.dto.DashboardDateRangeResponse;
import com.spark.falcon.dashboard.dto.DashboardPeriod;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
public class DashboardPeriodService {

    public DashboardDateRangeResponse resolve(String periodValue, String monthValue, LocalDate today) {
        DashboardPeriod period = DashboardPeriod.from(periodValue);
        YearMonth selectedMonth = parseMonth(monthValue, YearMonth.from(today));
        return switch (period) {
            case TODAY -> new DashboardDateRangeResponse(period, today, today, selectedMonth, "Today");
            case YESTERDAY -> {
                LocalDate day = today.minusDays(1);
                yield new DashboardDateRangeResponse(period, day, day, selectedMonth, "Yesterday");
            }
            case LAST_7_DAYS -> new DashboardDateRangeResponse(period, today.minusDays(6), today,
                    selectedMonth, "Last 7 Days");
            case LAST_30_DAYS -> new DashboardDateRangeResponse(period, today.minusDays(29), today,
                    selectedMonth, "Last 30 Days");
            case MONTH -> new DashboardDateRangeResponse(period, selectedMonth.atDay(1), selectedMonth.atEndOfMonth(),
                    selectedMonth, selectedMonth.toString());
        };
    }

    private YearMonth parseMonth(String value, YearMonth fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return YearMonth.parse(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
