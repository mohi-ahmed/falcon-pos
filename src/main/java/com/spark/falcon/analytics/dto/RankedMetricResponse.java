package com.spark.falcon.analytics.dto;

import java.math.BigDecimal;

public record RankedMetricResponse(Long id, String label, String secondaryLabel, BigDecimal value, BigDecimal quantity) { }
