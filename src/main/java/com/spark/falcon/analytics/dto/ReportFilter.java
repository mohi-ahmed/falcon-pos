package com.spark.falcon.analytics.dto;

import java.time.LocalDate;

public record ReportFilter(
        LocalDate from,
        LocalDate to,
        Long productId,
        Long categoryId,
        Long supplierId,
        Long customerId,
        Long paymentMethodId,
        String batchNumber,
        String expiryStatus,
        String query,
        String sort,
        int page,
        int size,
        boolean allBranches
) {
    public ReportFilter {
        query = clean(query);
        batchNumber = clean(batchNumber);
        expiryStatus = clean(expiryStatus);
        sort = clean(sort);
        if (page < 0) page = 0;
        if (size < 10) size = 25;
        if (size > 100) size = 100;
    }

    public int offset() { return page * size; }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
