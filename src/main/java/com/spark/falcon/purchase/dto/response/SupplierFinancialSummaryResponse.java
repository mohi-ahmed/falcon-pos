package com.spark.falcon.purchase.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierFinancialSummaryResponse(
        Long supplierId,
        BigDecimal totalConfirmedPurchases,
        BigDecimal totalPaid,
        BigDecimal outstandingDue,
        BigDecimal purchaseReturnsOrCredits,
        LocalDate lastPurchaseDate
) {
}
