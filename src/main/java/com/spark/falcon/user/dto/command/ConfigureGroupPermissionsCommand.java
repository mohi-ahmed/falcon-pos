package com.spark.falcon.user.dto.command;

import java.util.Set;

public record ConfigureGroupPermissionsCommand(
        ManagementActor actor,
        Long userGroupId,
        Set<Long> permissionIds
) {
}
