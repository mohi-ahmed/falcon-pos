package com.spark.falcon.dashboard.dto;

import java.util.Locale;

public enum DashboardPeriod {
    TODAY,
    YESTERDAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    MONTH;

    public static DashboardPeriod from(String value) {
        if (value == null || value.isBlank()) return TODAY;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "YESTERDAY" -> YESTERDAY;
            case "7", "7_DAYS", "LAST_7", "LAST_7_DAYS" -> LAST_7_DAYS;
            case "30", "30_DAYS", "LAST_30", "LAST_30_DAYS" -> LAST_30_DAYS;
            case "MONTH", "MONTHLY", "SELECTED_MONTH" -> MONTH;
            default -> TODAY;
        };
    }
}
