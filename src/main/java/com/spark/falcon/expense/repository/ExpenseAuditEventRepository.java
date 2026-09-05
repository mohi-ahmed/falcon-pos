package com.spark.falcon.expense.repository;
import com.spark.falcon.expense.entity.ExpenseAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ExpenseAuditEventRepository extends JpaRepository<ExpenseAuditEvent, Long> {
    List<ExpenseAuditEvent> findAllByExpenseIdOrderByOccurredAtAsc(Long expenseId);
}
