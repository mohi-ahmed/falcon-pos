package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.ProductBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductBatchRepository extends JpaRepository<ProductBatch, Long> {
    boolean existsByBusinessIdAndProductVariantIdIn(Long businessId, java.util.Collection<Long> productVariantIds);
    Optional<ProductBatch> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);

    List<ProductBatch> findByBusinessIdAndBranchIdAndProductVariantIdOrderByExpiryDateAscIdAsc(
            Long businessId, Long branchId, Long productVariantId);

    List<ProductBatch> findByBusinessIdAndBranchIdOrderByExpiryDateAscIdAsc(Long businessId, Long branchId);

    List<ProductBatch> findByBusinessIdAndBranchIdAndSupplierIdOrderByExpiryDateAscIdAsc(
            Long businessId, Long branchId, Long supplierId);

    Optional<ProductBatch> findByBusinessIdAndBranchIdAndProductVariantIdAndBatchNumberIgnoreCase(
            Long businessId, Long branchId, Long productVariantId, String batchNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select batch from ProductBatch batch
            where batch.id = :batchId
              and batch.businessId = :businessId
              and batch.branchId = :branchId
            """)
    Optional<ProductBatch> findForUpdate(@Param("businessId") Long businessId,
                                         @Param("branchId") Long branchId,
                                         @Param("batchId") Long batchId);
}
