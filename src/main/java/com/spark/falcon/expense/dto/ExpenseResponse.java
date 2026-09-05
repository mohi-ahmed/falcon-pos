package com.spark.falcon.expense.dto;

import com.spark.falcon.expense.entity.ExpenseClassification;
import com.spark.falcon.expense.entity.ExpenseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        Long branchId,
        LocalDate expenseDate,
        ExpenseClassification classification,
        Long categoryId,
        String category,
        String title,
        BigDecimal amount,
        Long paymentMethodId,
        String paymentMethod,
        String paymentReference,
        String paidFrom,
        Long cashLocationId,
        Long registerId,
        Long cashierShiftId,
        String payee,
        String attachmentReference,
        String notes,
        ExpenseStatus status,
        Long cashMovementId,
        Long reversalCashMovementId,
        String reversalReason,
        Long createdBy,
        Long approvedBy,
        Long postedBy,
        Instant createdAt,
        Instant postedAt
) { }
