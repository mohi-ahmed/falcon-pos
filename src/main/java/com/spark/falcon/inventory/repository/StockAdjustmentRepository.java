package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.dto.StockCountVarianceValueResponse;
import com.spark.falcon.inventory.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    Optional<StockAdjustment> findByIdAndBusinessId(Long id, Long businessId);
    Optional<StockAdjustment> findByBusinessIdAndIdempotencyKey(Long businessId, String key);
    List<StockAdjustment> findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(Long businessId, Long branchId);

    @Query("""
            select new com.spark.falcon.inventory.dto.StockCountVarianceValueResponse(
                    adjustment.sourceCountId,
                    sum(abs(adjustment.inventoryValueChange))
            )
            from StockAdjustment adjustment
            where adjustment.businessId = :businessId
              and adjustment.branchId = :branchId
              and adjustment.sourceType = :sourceType
              and adjustment.status in :statuses
              and adjustment.sourceCountId in :countIds
            group by adjustment.sourceCountId
            """)
    List<StockCountVarianceValueResponse> sumAbsoluteVarianceValueBySourceCountIds(
            @Param("businessId") Long businessId,
            @Param("branchId") Long branchId,
            @Param("sourceType") AdjustmentSourceType sourceType,
            @Param("statuses") List<InventoryOperationStatus> statuses,
            @Param("countIds") List<Long> countIds);

}
