package com.spark.falcon.branch.dto.command;

import com.spark.falcon.branch.entity.enumtype.BranchStatus;

public record ChangeBranchStatusCommand(Long ownerId, Long branchId, BranchStatus status) {
}
