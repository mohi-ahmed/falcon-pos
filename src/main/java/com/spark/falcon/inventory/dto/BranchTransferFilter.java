package com.spark.falcon.inventory.dto;

import com.spark.falcon.inventory.entity.BranchTransferStatus;

import java.time.Instant;

public record BranchTransferFilter(
        Long transferId,
        Long sourceBranchId,
        Long destinationBranchId,
        BranchTransferStatus status,
        Long createdBy,
        Long approvedBy,
        Long dispatchedBy,
        Long receivedBy,
        Instant from,
        Instant to
) {
}
