package com.spark.falcon.branch.dto.response;

import com.spark.falcon.branch.entity.enumtype.BranchStatus;

public record BranchAccessResponse(
        Long branchId,
        Long businessId,
        String branchName,
        String branchCode,
        String timeZone,
        String currency,
        BranchStatus status
) {
}
