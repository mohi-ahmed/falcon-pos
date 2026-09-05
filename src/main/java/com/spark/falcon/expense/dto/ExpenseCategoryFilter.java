package com.spark.falcon.expense.dto;

import java.time.LocalDate;

public record ExpenseCategoryFilter(
        Long parentCategoryId,
        Boolean topLevelOnly,
        Boolean active,
        LocalDate createdDate,
        Boolean used,
        String query
) {
}
