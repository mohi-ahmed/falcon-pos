package com.spark.falcon.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPerformanceResponse(LocalDate date, BigDecimal income, BigDecimal expense, BigDecimal netProfit) { }
