package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.PurchaseReturn;
import com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

    Optional<PurchaseReturn> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    Optional<PurchaseReturn> findByBusinessIdAndBranchIdAndIdempotencyKey(
            Long businessId, Long branchId, String idempotencyKey);

    boolean existsByBusinessIdAndBranchIdAndReferenceNumberIgnoreCase(
            Long businessId, Long branchId, String referenceNumber);

    boolean existsByBusinessIdAndBranchIdAndReferenceNumberIgnoreCaseAndIdNot(
            Long businessId, Long branchId, String referenceNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select value from PurchaseReturn value
            where value.id = :returnId
              and value.businessId = :businessId
              and value.branchId = :branchId
            """)
    Optional<PurchaseReturn> findForUpdate(@Param("businessId") Long businessId,
                                           @Param("branchId") Long branchId,
                                           @Param("returnId") Long returnId);

    @Query("""
            select value from PurchaseReturn value
            where value.businessId = :businessId
              and value.branchId = :branchId
              and (:supplierId is null or value.supplierId = :supplierId)
              and (:status is null or value.status = :status)
              and (:fromDate is null or value.returnDate >= :fromDate)
              and (:toDate is null or value.returnDate <= :toDate)
              and (:keyword = '' or lower(value.referenceNumber) like lower(concat('%', :keyword, '%')))
            """)
    Page<PurchaseReturn> search(@Param("businessId") Long businessId,
                                @Param("branchId") Long branchId,
                                @Param("supplierId") Long supplierId,
                                @Param("status") PurchaseReturnStatus status,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("keyword") String keyword,
                                Pageable pageable);
    @Query("""
            select coalesce(sum(value.refundAmount), 0)
            from PurchaseReturn value
            where value.businessId = :businessId
              and value.branchId = :branchId
              and value.supplierId = :supplierId
              and value.status = com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus.CONFIRMED
            """)
    BigDecimal sumConfirmedRefundAmountForSupplier(@Param("businessId") Long businessId,
                                                   @Param("branchId") Long branchId,
                                                   @Param("supplierId") Long supplierId);

    @Query("""
            select coalesce(sum(value.supplierCreditAmount), 0)
            from PurchaseReturn value
            where value.businessId = :businessId
              and value.branchId = :branchId
              and value.supplierId = :supplierId
              and value.status = com.spark.falcon.purchase.entity.enumtype.PurchaseReturnStatus.CONFIRMED
            """)
    BigDecimal sumConfirmedCreditAmountForSupplier(@Param("businessId") Long businessId,
                                                   @Param("branchId") Long branchId,
                                                   @Param("supplierId") Long supplierId);

}
