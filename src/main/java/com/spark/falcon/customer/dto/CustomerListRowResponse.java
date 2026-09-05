package com.spark.falcon.customer.dto;

public record CustomerListRowResponse(
        CustomerResponse customer,
        CustomerFinancialSummaryResponse financial
) {
}
