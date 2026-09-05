package com.spark.falcon.sale.repository;

import com.spark.falcon.sale.entity.SaleReturn;
import com.spark.falcon.sale.entity.SaleReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {
    Optional<SaleReturn> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    Optional<SaleReturn> findByBusinessIdAndBranchIdAndIdempotencyKey(
            Long businessId, Long branchId, String idempotencyKey);

    List<SaleReturn> findBySaleIdAndStatusOrderByCreatedAtAsc(Long saleId, SaleReturnStatus status);

    boolean existsBySaleIdAndStatus(Long saleId, SaleReturnStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select value from SaleReturn value
            where value.id = :returnId
              and value.businessId = :businessId
              and value.branchId = :branchId
            """)
    Optional<SaleReturn> findForUpdate(@Param("businessId") Long businessId,
                                       @Param("branchId") Long branchId,
                                       @Param("returnId") Long returnId);

    @Query("""
            select value from SaleReturn value
            where value.businessId = :businessId
              and value.branchId = :branchId
              and (:customerId is null or value.customerId = :customerId)
              and (:status is null or value.status = :status)
              and (:keyword is null or lower(value.referenceNumber) like lower(concat('%', :keyword, '%'))
                   or lower(cast(value.saleId as string)) like lower(concat('%', :keyword, '%'))
                   or lower(value.customerReference.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<SaleReturn> search(@Param("businessId") Long businessId,
                            @Param("branchId") Long branchId,
                            @Param("customerId") Long customerId,
                            @Param("status") SaleReturnStatus status,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    @Query("""
            select value from SaleReturn value
            where value.businessId = :businessId
              and value.branchId = :branchId
              and (:customerId is null or value.customerId = :customerId)
              and (:status is null or value.status = :status)
            """)
    Page<SaleReturn> searchWithoutKeyword(@Param("businessId") Long businessId,
                                          @Param("branchId") Long branchId,
                                          @Param("customerId") Long customerId,
                                          @Param("status") SaleReturnStatus status,
                                          Pageable pageable);

    @Query("""
            select value from SaleReturn value
            where value.businessId = :businessId
              and (:branchId is null or value.branchId = :branchId)
              and value.customerId = :customerId
              and value.status in (com.spark.falcon.sale.entity.SaleReturnStatus.CONFIRMED,
                                   com.spark.falcon.sale.entity.SaleReturnStatus.REVERSED)
            order by value.createdAt asc, value.id asc
            """)
    List<SaleReturn> findCustomerFinancialReturns(@Param("businessId") Long businessId,
                                                   @Param("branchId") Long branchId,
                                                   @Param("customerId") Long customerId);
}
