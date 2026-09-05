package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardShiftCashResponse(
        Long shiftId,
        String shiftCode,
        Long registerId,
        Long cashierUserId,
        Instant openedAt,
        BigDecimal expectedCash
) { }
