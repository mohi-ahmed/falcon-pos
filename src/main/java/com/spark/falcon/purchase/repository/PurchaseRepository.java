package com.spark.falcon.purchase.repository;

import com.spark.falcon.purchase.entity.Purchase;
import com.spark.falcon.purchase.entity.enumtype.PurchasePaymentStatus;
import com.spark.falcon.purchase.entity.enumtype.PurchaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);
    Optional<Purchase> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Purchase> findByBusinessIdAndBranchIdAndIdempotencyKey(Long businessId, Long branchId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select purchase from Purchase purchase
            where purchase.id = :purchaseId
              and purchase.businessId = :businessId
              and purchase.branchId = :branchId
            """)
    Optional<Purchase> findForUpdate(@Param("businessId") Long businessId,
                                     @Param("branchId") Long branchId,
                                     @Param("purchaseId") Long purchaseId);

    List<Purchase> findByBusinessIdAndBranchIdAndSupplierIdAndPaymentStatusInOrderByPurchaseDateAscIdAsc(
            Long businessId, Long branchId, Long supplierId, Collection<PurchasePaymentStatus> paymentStatuses);

    @Query("""
            select purchase from Purchase purchase
            where purchase.businessId = :businessId
              and purchase.branchId = :branchId
              and (:supplierId is null or purchase.supplierId = :supplierId)
              and (:status is null or purchase.status = :status)
              and (:paymentStatus is null or purchase.paymentStatus = :paymentStatus)
              and (:fromDate is null or purchase.purchaseDate >= :fromDate)
              and (:toDate is null or purchase.purchaseDate <= :toDate)
              and (
                    :keyword = '' or
                    lower(coalesce(purchase.supplierInvoiceReference, '')) like lower(concat('%', :keyword, '%')) or
                    cast(purchase.id as string) like concat('%', :keyword, '%')
              )
            """)
    Page<Purchase> search(@Param("businessId") Long businessId,
                          @Param("branchId") Long branchId,
                          @Param("supplierId") Long supplierId,
                          @Param("status") PurchaseStatus status,
                          @Param("paymentStatus") PurchasePaymentStatus paymentStatus,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          @Param("keyword") String keyword,
                          Pageable pageable);

    @Query("""
            select purchase from Purchase purchase
            where purchase.businessId = :businessId
              and (:branchId is null or purchase.branchId = :branchId)
              and purchase.supplierId = :supplierId
              and purchase.status in :statuses
            order by purchase.purchaseDate asc, purchase.id asc
            """)
    List<Purchase> findSupplierFinancialPurchases(@Param("businessId") Long businessId,
                                                   @Param("branchId") Long branchId,
                                                   @Param("supplierId") Long supplierId,
                                                   @Param("statuses") Collection<PurchaseStatus> statuses);
}
