package com.spark.falcon.dashboard.dto;

public record DashboardQuickAccessResponse(
        String code,
        String label,
        String targetUrl
) { }
