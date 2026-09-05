package com.spark.falcon.expense.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
public record ExpenseMonthwiseResponse(Map<LocalDate, Map<String, BigDecimal>> dailyCategoryAmounts,
        Map<String, BigDecimal> categoryTotals, BigDecimal grandTotal) { }
