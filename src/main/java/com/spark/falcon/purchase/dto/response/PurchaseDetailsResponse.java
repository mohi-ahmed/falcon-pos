package com.spark.falcon.purchase.dto.response;

import java.util.List;

public record PurchaseDetailsResponse(
        PurchaseResponse purchase,
        List<PurchaseAuditResponse> auditTimeline) {
}
