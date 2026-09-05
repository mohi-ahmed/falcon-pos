package com.spark.falcon.user.service;

import com.spark.falcon.user.dto.response.UserAccessResponse;

import java.util.Optional;

public interface UserAccessService {
    Optional<UserAccessResponse> findActiveByBusinessIdAndUserId(Long businessId, Long userId);
    Optional<UserAccessResponse> findActiveByBusinessIdAndEmail(Long businessId, String email);
    boolean hasActiveBranchAccess(Long businessId, Long userId, Long branchId);
    boolean hasPermission(Long businessId, Long userId, String permissionCode);
}
