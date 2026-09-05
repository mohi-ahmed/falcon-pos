package com.spark.falcon.user.dto.response;

import com.spark.falcon.user.entity.enumtype.UserStatus;

import java.util.Set;

public record UserAccessResponse(
        Long userId,
        Long businessId,
        String email,
        Long userGroupId,
        String userGroupName,
        UserStatus status,
        Set<Long> branchIds,
        Set<String> permissionCodes
) {
}
