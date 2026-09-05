package com.spark.falcon.expense.repository;

import com.spark.falcon.expense.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    Optional<ExpenseCategory> findByIdAndBusinessId(Long id, Long businessId);
    Optional<ExpenseCategory> findByIdAndBusinessIdAndActiveTrueAndArchivedFalse(Long id, Long businessId);
    Optional<ExpenseCategory> findByBusinessIdAndSlugAndArchivedFalse(Long businessId, String slug);
    Optional<ExpenseCategory> findByBusinessIdAndSlug(Long businessId, String slug);
    List<ExpenseCategory> findAllByBusinessIdAndArchivedFalseOrderByDisplayOrderAscNameAsc(Long businessId);
}
