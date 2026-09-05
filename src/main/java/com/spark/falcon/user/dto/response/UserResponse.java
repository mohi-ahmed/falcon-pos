package com.spark.falcon.user.dto.response;

import com.spark.falcon.user.entity.enumtype.UserStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record UserResponse(
        Long id,
        Long businessId,
        String fullName,
        String email,
        String mobileNumber,
        LocalDate dateOfBirth,
        String profilePhotoReference,
        Long userGroupId,
        String userGroupName,
        UserStatus status,
        Set<Long> branchIds,
        Instant createdAt,
        boolean archived
) {
}
