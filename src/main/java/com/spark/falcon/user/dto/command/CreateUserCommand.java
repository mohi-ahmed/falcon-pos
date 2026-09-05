package com.spark.falcon.user.dto.command;

import com.spark.falcon.user.entity.enumtype.UserStatus;

import java.time.LocalDate;
import java.util.Set;

public record CreateUserCommand(
        ManagementActor actor,
        String fullName,
        String email,
        String mobileNumber,
        String rawPassword,
        String passwordConfirmation,
        LocalDate dateOfBirth,
        String profilePhotoReference,
        Long userGroupId,
        Set<Long> branchIds,
        UserStatus status
) {
}
