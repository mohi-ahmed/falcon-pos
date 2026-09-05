package com.spark.falcon.purchase.dto.response;

import com.spark.falcon.purchase.entity.enumtype.PurchaseActorType;
import com.spark.falcon.purchase.entity.enumtype.PurchaseAuditAction;

import java.time.Instant;

public record PurchaseAuditResponse(
        Long id,
        Long purchaseId,
        PurchaseAuditAction action,
        String subjectType,
        Long subjectId,
        PurchaseActorType actorType,
        Long actorId,
        String details,
        Instant createdAt) {
}
