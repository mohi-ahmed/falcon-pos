package com.spark.falcon.businesssetup.dto.response;

public record BusinessSetupResponse(
        Long businessId,
        Long branchId,
        String businessName,
        String businessCode,
        String branchName,
        String branchCode,
        String ownerName
) {
}
