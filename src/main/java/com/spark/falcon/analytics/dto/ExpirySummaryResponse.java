package com.spark.falcon.analytics.dto;

import java.math.BigDecimal;

public record ExpirySummaryResponse(
        long expiringWithin7Products,
        long expiringWithin30Products,
        BigDecimal expiredQuantity,
        BigDecimal expiredStockValue,
        BigDecimal currentMonthExpiryLoss
) { }
