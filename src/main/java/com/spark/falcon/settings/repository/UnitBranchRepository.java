package com.spark.falcon.settings.repository;

import com.spark.falcon.settings.entity.UnitBranch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitBranchRepository extends JpaRepository<UnitBranch, Long> {
    List<UnitBranch> findByUnitId(Long unitId);
    List<UnitBranch> findByBranchIdAndActiveTrue(Long branchId);
    Optional<UnitBranch> findByUnitIdAndBranchId(Long unitId, Long branchId);
    boolean existsByUnitIdAndBranchIdAndActiveTrue(Long unitId, Long branchId);
}
