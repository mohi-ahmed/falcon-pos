package com.spark.falcon.dashboard.dto;

public record DashboardAccessResponse(
        boolean financial,
        boolean sales,
        boolean purchase,
        boolean inventory,
        boolean payment,
        boolean expense,
        boolean cash,
        boolean customer,
        boolean supplier,
        boolean analytics
) { }
