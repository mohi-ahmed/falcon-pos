package com.spark.falcon.settings.service;

import com.spark.falcon.settings.dto.response.UnitResponse;

import java.util.List;
import java.util.Optional;

public interface UnitAccessService {
    Optional<UnitResponse> findActiveForBranch(Long businessId, Long branchId, Long unitId);
    List<UnitResponse> findActiveForBranch(Long businessId, Long branchId);
}
