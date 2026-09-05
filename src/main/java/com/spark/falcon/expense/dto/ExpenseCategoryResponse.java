package com.spark.falcon.expense.dto;

import java.time.Instant;

public record ExpenseCategoryResponse(Long id, String name, String slug, Long parentCategoryId,
        String parentCategory, String description, boolean active, int displayOrder,
        long postedExpenseCount, boolean used, boolean archived, Instant createdAt) { }
