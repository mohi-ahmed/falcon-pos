package com.spark.falcon.sale.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerSaleStatementEntryResponse(
        Instant transactionAt,
        String reference,
        Long branchId,
        String type,
        BigDecimal debit,
        BigDecimal credit,
        String sourcePath
) {
}
