package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.InventoryOperationStatus;
import com.spark.falcon.inventory.entity.PhysicalStockCount;
import com.spark.falcon.inventory.entity.StockCountScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhysicalStockCountRepository extends JpaRepository<PhysicalStockCount, Long> {

    Optional<PhysicalStockCount> findByIdAndBusinessId(Long id, Long businessId);

    Optional<PhysicalStockCount> findByBusinessIdAndIdempotencyKey(Long businessId, String key);

    List<PhysicalStockCount> findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(Long businessId, Long branchId);

}
