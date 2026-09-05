package com.spark.falcon.purchase.dto.command;

import java.math.BigDecimal;

public record PurchaseReturnItemCommand(
        Long purchaseItemId,
        Long returnUnitId,
        BigDecimal returnQuantity) {
}
