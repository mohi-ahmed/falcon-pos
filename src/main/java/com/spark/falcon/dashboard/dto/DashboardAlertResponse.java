package com.spark.falcon.dashboard.dto;

public record DashboardAlertResponse(
        String code,
        String label,
        long count,
        String severity,
        String targetUrl
) { }
