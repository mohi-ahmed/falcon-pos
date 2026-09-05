package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.BranchSettingsResponse;

import java.util.Optional;

public interface BranchSettingsAccessService {
    Optional<BranchSettingsResponse> findByBusinessIdAndBranchId(Long businessId, Long branchId);
}
