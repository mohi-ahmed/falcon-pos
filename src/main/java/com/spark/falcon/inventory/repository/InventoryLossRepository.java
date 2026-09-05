package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.InventoryLoss;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryLossRepository extends JpaRepository<InventoryLoss, Long> {
    Optional<InventoryLoss> findByIdAndBusinessId(Long id, Long businessId);
    Optional<InventoryLoss> findByBusinessIdAndIdempotencyKey(Long businessId, String key);
    List<InventoryLoss> findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(Long businessId, Long branchId);

    @Query("""
            select coalesce(sum(loss.financialLoss), 0)
            from InventoryLoss loss
            where loss.businessId = :businessId
              and loss.branchId = :branchId
              and loss.status = com.spark.falcon.inventory.entity.InventoryOperationStatus.POSTED
              and exists (
                    select batch.id from ProductBatch batch
                    where batch.id = loss.productBatchId
                      and batch.supplierId = :supplierId
                      and batch.expiryDate is not null
                      and batch.expiryDate < loss.disposalDate
                  )
            """)
    BigDecimal sumPostedExpiryLossForSupplier(@Param("businessId") Long businessId,
                                              @Param("branchId") Long branchId,
                                              @Param("supplierId") Long supplierId);

}
