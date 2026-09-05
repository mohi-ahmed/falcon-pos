package com.spark.falcon.supplier.repository;

import com.spark.falcon.supplier.entity.SupplierBranch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierBranchRepository extends JpaRepository<SupplierBranch, Long> {

    boolean existsBySupplierIdAndBranchId(Long supplierId, Long branchId);

    List<SupplierBranch> findBySupplierIdOrderByBranchIdAsc(Long supplierId);
}
