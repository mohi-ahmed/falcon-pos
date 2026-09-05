package com.spark.falcon.customer.dto;

import java.time.LocalDate;

public record CustomerListFilter(
        String keyword,
        Boolean archived,
        Boolean hasDue,
        Boolean overdue,
        LocalDate createdFrom,
        LocalDate createdTo,
        LocalDate lastPurchaseFrom,
        LocalDate lastPurchaseTo,
        String sort
) {
}
