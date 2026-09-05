package com.spark.falcon.dashboard.dto;

import java.math.BigDecimal;

public record DashboardInventorySummaryResponse(
        BigDecimal sellableStockQuantity,
        BigDecimal currentStockValue,
        long lowStockProducts,
        long outOfStockProducts,
        long expiringWithin7Days,
        long expiringWithin30Days,
        BigDecimal expiredStockQuantity,
        BigDecimal expiredStockValue,
        BigDecimal currentMonthInventoryLoss,
        long pendingBranchTransfers,
        long transferDiscrepancies,
        long unresolvedPhysicalCountVariances,
        long failedStockImports,
        long stockImportsAwaitingConfirmation
) { }
