package com.spark.falcon.sale.repository;

import com.spark.falcon.sale.entity.SaleItemBatchAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SaleItemBatchAllocationRepository extends JpaRepository<SaleItemBatchAllocation, Long> {
    List<SaleItemBatchAllocation> findBySaleItemIdOrderByIdAsc(Long saleItemId);

    Optional<SaleItemBatchAllocation> findByIdAndSaleItemId(Long id, Long saleItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select allocation from SaleItemBatchAllocation allocation
            where allocation.saleItemId = :saleItemId
            order by allocation.expiryDateSnapshot asc, allocation.id asc
            """)
    List<SaleItemBatchAllocation> findForUpdateBySaleItemId(@Param("saleItemId") Long saleItemId);
}
