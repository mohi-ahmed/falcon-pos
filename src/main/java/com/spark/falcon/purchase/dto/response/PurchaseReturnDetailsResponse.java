package com.spark.falcon.purchase.dto.response;

import java.util.List;

public record PurchaseReturnDetailsResponse(
        PurchaseReturnResponse purchaseReturn,
        List<PurchaseAuditResponse> auditTimeline) {
}
