package com.spark.falcon.inventory.dto;import java.math.BigDecimal;import java.util.List;
public record ReceiveBranchTransferCommand(Long ownerId,Long transferId,String idempotencyKey,List<ItemReceipt> items){public record ItemReceipt(Long transferItemId,BigDecimal acceptedQuantity,BigDecimal damagedQuantity,BigDecimal missingQuantity,BigDecimal rejectedQuantity,String discrepancyReason){}}
