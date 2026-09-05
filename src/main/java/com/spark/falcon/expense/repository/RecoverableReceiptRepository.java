package com.spark.falcon.expense.repository;
import com.spark.falcon.expense.entity.RecoverableReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RecoverableReceiptRepository extends JpaRepository<RecoverableReceipt,Long>{
    Optional<RecoverableReceipt> findByBusinessIdAndIdempotencyKey(Long businessId,String key);
    List<RecoverableReceipt> findAllByBusinessIdAndBranchIdAndExpenseId(Long businessId,Long branchId,Long expenseId);
    List<RecoverableReceipt> findAllByBusinessIdAndBranchId(Long businessId,Long branchId);
    List<RecoverableReceipt> findAllByBusinessIdAndBranchIdAndReceivedAtBetween(Long businessId,Long branchId,
            java.time.Instant from,java.time.Instant to);
}
