package com.spark.falcon.inventory.dto;import java.math.BigDecimal;
public record RecordStockCountRowCommand(Long ownerId,Long countId,Long rowId,BigDecimal countedQuantity,Long countedUnitId,BigDecimal conversionFactor,String varianceReason){}
