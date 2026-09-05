package com.spark.falcon.analytics.dto;

import java.util.List;

public record ReportFilterOptionsResponse(
        List<FilterOptionResponse> products,
        List<FilterOptionResponse> categories,
        List<FilterOptionResponse> suppliers,
        List<FilterOptionResponse> customers,
        List<FilterOptionResponse> paymentMethods
) { }
