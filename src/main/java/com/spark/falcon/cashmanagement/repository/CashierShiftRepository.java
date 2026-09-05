package com.spark.falcon.cashmanagement.repository;

import com.spark.falcon.cashmanagement.entity.CashVarianceResult;
import com.spark.falcon.cashmanagement.entity.CashierShift;
import com.spark.falcon.cashmanagement.entity.CashierShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CashierShiftRepository extends JpaRepository<CashierShift, Long> {
    List<CashierShift> findByBusinessIdAndBranchIdOrderByOpeningTimeDesc(Long businessId, Long branchId);
    Optional<CashierShift> findByBusinessIdAndBranchIdAndOpenKey(Long businessId, Long branchId, String openKey);
    boolean existsByRegisterIdAndStatus(Long registerId, CashierShiftStatus status);
    boolean existsByBusinessIdAndBranchIdAndCashierUserIdAndStatus(Long businessId, Long branchId,
                                                                  Long cashierUserId, CashierShiftStatus status);
    long countByBusinessIdAndBranchIdAndStatus(Long businessId, Long branchId, CashierShiftStatus status);
    Optional<CashierShift> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    @Query("""
            select s from CashierShift s where s.businessId = :businessId and s.branchId = :branchId
              and (:registerId is null or s.registerId = :registerId)
              and (:cashierId is null or s.cashierUserId = :cashierId)
              and (:status is null or s.status = :status)
              and (:varianceResult is null or s.varianceResult = :varianceResult)
              and (:approvalRequired is null or s.varianceApprovalRequired = :approvalRequired)
              and s.openingTime >= :fromTime
              and s.openingTime < :toTime
              and (:query = '' or lower(s.shiftCode) like lower(concat('%', :query, '%')))
            """)
    Page<CashierShift> search(@Param("businessId") Long businessId, @Param("branchId") Long branchId,
                              @Param("registerId") Long registerId, @Param("cashierId") Long cashierId,
                              @Param("status") CashierShiftStatus status, @Param("varianceResult") CashVarianceResult varianceResult,
                              @Param("approvalRequired") Boolean approvalRequired, @Param("fromTime") Instant fromTime,
                              @Param("toTime") Instant toTime, @Param("query") String query, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashierShift s where s.id = :shiftId and s.businessId = :businessId and s.branchId = :branchId")
    Optional<CashierShift> findForUpdate(@Param("businessId") Long businessId,
                                         @Param("branchId") Long branchId,
                                         @Param("shiftId") Long shiftId);
}
