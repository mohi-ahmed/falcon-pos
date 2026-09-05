package com.spark.falcon.branch.dto.response;

import com.spark.falcon.branch.entity.enumtype.BranchAuditAction;

import java.time.Instant;

public record BranchAuditResponse(
        Long id,
        Long actorOwnerId,
        BranchAuditAction action,
        String beforeSnapshot,
        String afterSnapshot,
        Instant occurredAt
) {
}
