package com.spark.falcon.cashmanagement.repository;

import com.spark.falcon.cashmanagement.entity.CashLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashLocationRepository extends JpaRepository<CashLocation, Long> {
    List<CashLocation> findByBusinessIdAndBranchIdOrderByNameAsc(Long businessId, Long branchId);
    boolean existsByBranchIdAndNameIgnoreCase(Long branchId, String name);
    Optional<CashLocation> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);
}
