package com.spark.falcon.cashmanagement.repository;

import com.spark.falcon.cashmanagement.entity.CashMovement;
import com.spark.falcon.cashmanagement.entity.CashMovementStatus;
import com.spark.falcon.cashmanagement.entity.CashMovementType;
import com.spark.falcon.cashmanagement.entity.CashSourceModule;
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

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
    Optional<CashMovement> findByBusinessIdAndBranchIdAndPostingKey(Long businessId, Long branchId, String postingKey);
    Optional<CashMovement> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CashMovement m where m.businessId = :businessId and m.branchId = :branchId and m.postingKey = :postingKey")
    Optional<CashMovement> findPostingForUpdate(@Param("businessId") Long businessId,
                                                @Param("branchId") Long branchId,
                                                @Param("postingKey") String postingKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CashMovement m where m.id = :movementId and m.businessId = :businessId and m.branchId = :branchId")
    Optional<CashMovement> findForUpdate(@Param("businessId") Long businessId,
                                         @Param("branchId") Long branchId,
                                         @Param("movementId") Long movementId);

    List<CashMovement> findAllByBusinessIdAndBranchIdOrderByPostedAtDesc(Long businessId, Long branchId);

    @Query("""
            select m from CashMovement m where m.businessId = :businessId and m.branchId = :branchId
              and (:cashLocationId is null or m.cashLocationId = :cashLocationId or m.destinationCashLocationId = :cashLocationId)
              and (:registerId is null or m.registerId = :registerId or m.destinationRegisterId = :registerId)
              and (:shiftId is null or m.cashierShiftId = :shiftId or m.destinationCashierShiftId = :shiftId)
              and (:movementType is null or m.movementType = :movementType)
              and (:sourceModule is null or m.sourceModule = :sourceModule)
              and (:userId is null or m.postedByUserId = :userId)
              and (:status is null or m.status = :status)
              and m.postedAt >= :fromTime
              and m.postedAt < :toTime
              and (:query = '' or lower(coalesce(m.sourceReference, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(m.sourceTransactionId, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(m.note, '')) like lower(concat('%', :query, '%')))
            """)
    Page<CashMovement> search(@Param("businessId") Long businessId, @Param("branchId") Long branchId,
                              @Param("cashLocationId") Long cashLocationId, @Param("registerId") Long registerId,
                              @Param("shiftId") Long shiftId, @Param("movementType") CashMovementType movementType,
                              @Param("sourceModule") CashSourceModule sourceModule, @Param("userId") Long userId,
                              @Param("status") CashMovementStatus status, @Param("fromTime") Instant fromTime,
                              @Param("toTime") Instant toTime, @Param("query") String query, Pageable pageable);

    @Query("select m from CashMovement m where m.cashierShiftId = :shiftId or m.destinationCashierShiftId = :shiftId order by m.postedAt asc, m.id asc")
    List<CashMovement> findAllForShiftOrderByPostedAtAsc(@Param("shiftId") Long shiftId);

    @Query("""
            select m from CashMovement m
            where m.businessId = :businessId and m.branchId = :branchId
              and (m.cashierShiftId in :shiftIds or m.destinationCashierShiftId in :shiftIds)
            order by m.postedAt asc, m.id asc
            """)
    List<CashMovement> findAllForShifts(@Param("businessId") Long businessId,
                                        @Param("branchId") Long branchId,
                                        @Param("shiftIds") List<Long> shiftIds);
}
