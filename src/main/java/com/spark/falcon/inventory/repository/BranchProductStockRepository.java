package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.BranchProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchProductStockRepository extends JpaRepository<BranchProductStock, Long> {

    List<BranchProductStock> findByBusinessIdAndBranchIdOrderByProductVariantIdAsc(Long businessId, Long branchId);

    Optional<BranchProductStock> findByBusinessIdAndBranchIdAndProductVariantId(
            Long businessId, Long branchId, Long productVariantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select stock from BranchProductStock stock
            where stock.businessId = :businessId
              and stock.branchId = :branchId
              and stock.productVariantId = :productVariantId
            """)
    Optional<BranchProductStock> findForUpdate(@Param("businessId") Long businessId,
                                               @Param("branchId") Long branchId,
                                               @Param("productVariantId") Long productVariantId);
}
