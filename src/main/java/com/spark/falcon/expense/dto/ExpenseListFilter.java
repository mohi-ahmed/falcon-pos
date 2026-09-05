package com.spark.falcon.expense.dto;

import com.spark.falcon.expense.entity.ExpenseClassification;
import com.spark.falcon.expense.entity.ExpenseStatus;

import java.time.LocalDate;

public record ExpenseListFilter(
        Long expenseId,
        ExpenseClassification classification,
        Long categoryId,
        Long paymentMethodId,
        ExpenseStatus status,
        Long createdBy,
        LocalDate from,
        LocalDate to,
        String query
) {
}
