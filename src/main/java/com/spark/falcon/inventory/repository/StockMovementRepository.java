package com.spark.falcon.inventory.repository;

import com.spark.falcon.inventory.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    boolean existsByBusinessIdAndProductVariantIdIn(Long businessId, java.util.Collection<Long> productVariantIds);
    Optional<StockMovement> findByBusinessIdAndBranchIdAndPostingKey(Long businessId, Long branchId, String postingKey);
    Optional<StockMovement> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);
    boolean existsByBusinessIdAndBranchIdAndProductVariantId(Long businessId, Long branchId, Long productVariantId);
    boolean existsByReversalOfMovementId(Long reversalOfMovementId);
    Optional<StockMovement> findFirstByReversalOfMovementId(Long reversalOfMovementId);
    List<StockMovement> findByBusinessIdAndBranchIdOrderByPostedAtDescIdDesc(Long businessId, Long branchId);
    List<StockMovement> findByBusinessIdAndBranchIdAndProductVariantIdOrderByPostedAtDescIdDesc(Long businessId, Long branchId, Long productVariantId);
    List<StockMovement> findByBusinessIdAndBranchIdAndSourceTypeAndSourceReferenceIdOrderByIdAsc(Long businessId, Long branchId, StockSourceType sourceType, String sourceReferenceId);

}
