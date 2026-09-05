package com.spark.falcon.cashmanagement.repository;

import com.spark.falcon.cashmanagement.entity.Cashbook;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CashbookRepository extends JpaRepository<Cashbook, Long> {
    Optional<Cashbook> findByBusinessIdAndBranchId(Long businessId, Long branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cashbook c where c.businessId = :businessId and c.branchId = :branchId")
    Optional<Cashbook> findForUpdate(@Param("businessId") Long businessId, @Param("branchId") Long branchId);
}
