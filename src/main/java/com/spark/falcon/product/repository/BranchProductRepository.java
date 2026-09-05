package com.spark.falcon.product.repository;

import com.spark.falcon.product.entity.BranchProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchProductRepository extends JpaRepository<BranchProduct, Long> {
    List<BranchProduct> findByProductId(Long productId);
    Optional<BranchProduct> findByProductIdAndBranchId(Long productId, Long branchId);
    boolean existsByProductIdAndBranchIdAndActiveTrue(Long productId, Long branchId);
    List<BranchProduct> findByProductIdIn(List<Long> productIds);
}
