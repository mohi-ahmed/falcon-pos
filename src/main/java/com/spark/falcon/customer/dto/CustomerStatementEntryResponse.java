package com.spark.falcon.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerStatementEntryResponse(
        Instant transactionAt,
        String reference,
        Long branchId,
        String type,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal runningDueBalance,
        String sourcePath
) {
}
