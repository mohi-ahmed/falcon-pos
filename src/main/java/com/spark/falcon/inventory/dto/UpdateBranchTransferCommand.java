package com.spark.falcon.inventory.dto;

import java.time.LocalDate;
import java.util.List;

public record UpdateBranchTransferCommand(
        Long ownerId,
        Long transferId,
        Long destinationBranchId,
        LocalDate requestDate,
        LocalDate expectedDispatchDate,
        String notes,
        String attachmentReference,
        List<CreateBranchTransferCommand.Item> items
) {
}
