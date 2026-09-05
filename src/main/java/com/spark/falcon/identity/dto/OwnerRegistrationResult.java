package com.spark.falcon.identity.dto;

import com.spark.falcon.identity.entity.OwnerStatus;

public record OwnerRegistrationResult(
        Long ownerId,
        String email,
        OwnerStatus status
) {
}

