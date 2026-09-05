package com.spark.falcon.product.entity;

import com.spark.falcon.product.entity.enumtype.ProductAuditAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "product_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "actor_owner_id", nullable = false, updatable = false)
    private Long actorOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private ProductAuditAction action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static ProductAuditEvent record(Long businessId, Long productId, Long actorOwnerId,
                                           ProductAuditAction action, String targetType, Long targetId,
                                           Instant occurredAt) {
        ProductAuditEvent event = new ProductAuditEvent();
        event.businessId = Objects.requireNonNull(businessId, "businessId is required");
        event.productId = Objects.requireNonNull(productId, "productId is required");
        event.actorOwnerId = Objects.requireNonNull(actorOwnerId, "actorOwnerId is required");
        event.action = Objects.requireNonNull(action, "action is required");
        event.targetType = Objects.requireNonNull(targetType, "targetType is required");
        event.targetId = targetId;
        event.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        return event;
    }
}
