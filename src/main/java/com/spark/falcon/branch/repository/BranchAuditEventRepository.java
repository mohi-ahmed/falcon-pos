package com.spark.falcon.branch.repository;

import com.spark.falcon.branch.entity.BranchAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchAuditEventRepository extends JpaRepository<BranchAuditEvent, Long> {
    List<BranchAuditEvent> findByBusinessIdAndBranchIdOrderByOccurredAtDescIdDesc(Long businessId, Long branchId);
}
