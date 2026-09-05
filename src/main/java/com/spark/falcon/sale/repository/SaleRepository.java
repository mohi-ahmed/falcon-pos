package com.spark.falcon.sale.repository;

import com.spark.falcon.sale.entity.Sale;
import com.spark.falcon.sale.entity.SalePaymentStatus;
import com.spark.falcon.sale.entity.SaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    Optional<Sale> findByBusinessIdAndBranchIdAndIdempotencyKey(
            Long businessId, Long branchId, String idempotencyKey);

    List<Sale> findByBusinessIdAndBranchIdAndIdIn(Long businessId, Long branchId, Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sale from Sale sale
            where sale.id = :saleId
              and sale.businessId = :businessId
              and sale.branchId = :branchId
            """)
    Optional<Sale> findForUpdate(@Param("businessId") Long businessId,
                                 @Param("branchId") Long branchId,
                                 @Param("saleId") Long saleId);

    @Query("""
            select sale from Sale sale
            where sale.businessId = :businessId
              and sale.branchId = :branchId
              and (:customerId is null or sale.customerId = :customerId)
              and (:status is null or sale.status = :status)
              and (:paymentStatus is null or sale.paymentStatus = :paymentStatus)
              and (:fromTime is null or sale.createdAt >= :fromTime)
              and (:toTime is null or sale.createdAt < :toTime)
              and (:keyword is null or lower(cast(sale.id as string)) like lower(concat('%', :keyword, '%'))
                   or lower(sale.customerReference.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Sale> search(@Param("businessId") Long businessId,
                      @Param("branchId") Long branchId,
                      @Param("customerId") Long customerId,
                      @Param("status") SaleStatus status,
                      @Param("paymentStatus") SalePaymentStatus paymentStatus,
                      @Param("fromTime") Instant fromTime,
                      @Param("toTime") Instant toTime,
                      @Param("keyword") String keyword,
                      Pageable pageable);

    @Query("""
            select sale from Sale sale
            where sale.businessId = :businessId
              and sale.branchId = :branchId
              and (:customerId is null or sale.customerId = :customerId)
              and (:filter = 'ALL'
                   or (:filter = 'TODAY' and sale.createdAt >= :fromTime and sale.createdAt < :toTime)
                   or (:filter = 'DUE' and sale.dueAmount > 0
                       and sale.status in (com.spark.falcon.sale.entity.SaleStatus.CONFIRMED,
                                           com.spark.falcon.sale.entity.SaleStatus.PARTIALLY_RETURNED))
                   or (:filter = 'PAID' and sale.paymentStatus = com.spark.falcon.sale.entity.SalePaymentStatus.PAID
                       and sale.status not in (com.spark.falcon.sale.entity.SaleStatus.VOIDED,
                                               com.spark.falcon.sale.entity.SaleStatus.REVERSED))
                   or (:filter = 'INACTIVE' and sale.status in (com.spark.falcon.sale.entity.SaleStatus.VOIDED,
                                                                com.spark.falcon.sale.entity.SaleStatus.REVERSED)))
              and (:keyword is null or cast(sale.id as string) like concat('%', :keyword, '%')
                   or lower(sale.customerReference.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Sale> searchSellList(@Param("businessId") Long businessId,
                              @Param("branchId") Long branchId,
                              @Param("customerId") Long customerId,
                              @Param("filter") String filter,
                              @Param("fromTime") Instant fromTime,
                              @Param("toTime") Instant toTime,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    @Query("""
            select sale from Sale sale
            where sale.businessId = :businessId
              and sale.branchId = :branchId
              and (:customerId is null or sale.customerId = :customerId)
              and (:filter = 'ALL'
                   or (:filter = 'TODAY' and sale.createdAt >= :fromTime and sale.createdAt < :toTime)
                   or (:filter = 'DUE' and sale.dueAmount > 0
                       and sale.status in (com.spark.falcon.sale.entity.SaleStatus.CONFIRMED,
                                           com.spark.falcon.sale.entity.SaleStatus.PARTIALLY_RETURNED))
                   or (:filter = 'PAID' and sale.paymentStatus = com.spark.falcon.sale.entity.SalePaymentStatus.PAID
                       and sale.status not in (com.spark.falcon.sale.entity.SaleStatus.VOIDED,
                                               com.spark.falcon.sale.entity.SaleStatus.REVERSED))
                   or (:filter = 'INACTIVE' and sale.status in (com.spark.falcon.sale.entity.SaleStatus.VOIDED,
                                                                com.spark.falcon.sale.entity.SaleStatus.REVERSED)))
            """)
    Page<Sale> searchSellListWithoutKeyword(@Param("businessId") Long businessId,
                                            @Param("branchId") Long branchId,
                                            @Param("customerId") Long customerId,
                                            @Param("filter") String filter,
                                            @Param("fromTime") Instant fromTime,
                                            @Param("toTime") Instant toTime,
                                            Pageable pageable);

    @Query("""
            select sale from Sale sale
            where sale.businessId = :businessId
              and sale.branchId = :branchId
              and (:customerId is null or sale.customerId = :customerId)
              and sale.dueAmount > 0
              and sale.status in :statuses
            order by sale.createdAt asc, sale.id asc
            """)
    List<Sale> findDueInvoicesOldestFirst(@Param("businessId") Long businessId,
                                          @Param("branchId") Long branchId,
                                          @Param("customerId") Long customerId,
                                          @Param("statuses") List<SaleStatus> statuses);

    @Query("""
            select sale from Sale sale
            where sale.businessId = :businessId
              and (:branchId is null or sale.branchId = :branchId)
              and (:customerId is null or sale.customerId = :customerId)
              and sale.status in :statuses
            order by sale.createdAt asc, sale.id asc
            """)
    List<Sale> findCustomerFinancialSales(@Param("businessId") Long businessId,
                                          @Param("branchId") Long branchId,
                                          @Param("customerId") Long customerId,
                                          @Param("statuses") List<SaleStatus> statuses);
}
