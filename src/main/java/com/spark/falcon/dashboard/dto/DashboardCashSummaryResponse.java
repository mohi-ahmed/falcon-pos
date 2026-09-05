package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardCashSummaryResponse(
        long activeShifts,
        long shiftsAwaitingClosure,
        long unresolvedShortageOrExcess,
        BigDecimal branchConsolidatedCash,
        BigDecimal cashbookClosingBalance,
        List<DashboardShiftCashResponse> registerExpectedCash
) { }
