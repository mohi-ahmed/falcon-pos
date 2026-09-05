package com.spark.falcon.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerPaymentStatementEntryResponse(
        Instant transactionAt,
        String reference,
        Long branchId,
        String type,
        BigDecimal debit,
        BigDecimal credit,
        String sourcePath
) {
}
