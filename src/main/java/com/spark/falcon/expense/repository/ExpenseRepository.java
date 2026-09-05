package com.spark.falcon.expense.repository;

import com.spark.falcon.expense.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Optional<Expense> findByIdAndBusinessId(Long id, Long businessId);
    Optional<Expense> findByBusinessIdAndBranchIdAndIdempotencyKey(Long businessId, Long branchId, String key);
    List<Expense> findAllByBusinessIdAndBranchIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
            Long businessId, Long branchId, LocalDate from, LocalDate to);
    long countByBusinessIdAndCategoryIdAndStatus(Long businessId, Long categoryId, ExpenseStatus status);
    long countByBusinessIdAndCategoryIdAndStatusIn(Long businessId, Long categoryId, Collection<ExpenseStatus> statuses);
    boolean existsByCategoryId(Long categoryId);
}
