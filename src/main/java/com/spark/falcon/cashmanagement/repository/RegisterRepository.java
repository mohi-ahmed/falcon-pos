package com.spark.falcon.cashmanagement.repository;

import com.spark.falcon.cashmanagement.entity.Register;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegisterRepository extends JpaRepository<Register, Long> {
    List<Register> findByBusinessIdAndBranchIdOrderByNameAsc(Long businessId, Long branchId);
    boolean existsByBranchIdAndCodeIgnoreCase(Long branchId, String code);
    Optional<Register> findByIdAndBusinessIdAndBranchId(Long id, Long businessId, Long branchId);
}
