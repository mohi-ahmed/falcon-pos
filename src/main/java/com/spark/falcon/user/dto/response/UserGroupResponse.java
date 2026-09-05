package com.spark.falcon.user.dto.response;

import java.time.Instant;
import java.util.Set;

public record UserGroupResponse(
        Long id,
        Long businessId,
        String name,
        String slug,
        long userCount,
        Set<Long> permissionIds,
        boolean protectedGroup,
        boolean archived,
        Instant createdAt
) {
}
