package com.spark.falcon.branch.service;

import com.spark.falcon.branch.dto.response.BranchAccessResponse;

import java.util.List;
import java.util.Optional;

public interface BranchAccessService {

    Optional<BranchAccessResponse> findByBusinessIdAndBranchId(Long businessId, Long branchId);

    Optional<BranchAccessResponse> findActiveByBusinessIdAndBranchId(Long businessId, Long branchId);

    List<BranchAccessResponse> findActiveByBusinessId(Long businessId);
}
