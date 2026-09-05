package com.spark.falcon.expense.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ExpenseSummaryResponse(Map<String, BigDecimal> operatingExpenseByCategory,
        BigDecimal totalOperatingExpense, BigDecimal recoverableDepositsPaid,
        BigDecimal recoverableAmountReceived, BigDecimal outstandingRecoverableBalance) { }
