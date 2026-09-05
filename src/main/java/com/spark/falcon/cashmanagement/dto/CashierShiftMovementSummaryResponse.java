package com.spark.falcon.cashmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashierShiftMovementSummaryResponse(
        BigDecimal cashInflows,
        BigDecimal cashOutflows,
        BigDecimal transfersIn,
        BigDecimal transfersOut,
        long transactionCount,
        Instant lastActivity
) {
    public static CashierShiftMovementSummaryResponse empty() {
        return new CashierShiftMovementSummaryResponse(
                BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4),
                BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4), 0L, null);
    }
}
