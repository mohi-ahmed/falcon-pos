package com.spark.falcon.businesssetup.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "business_setup_audits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessSetupAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false, updatable = false) private Long ownerId;
    @Column(name = "business_id", nullable = false, updatable = false) private Long businessId;
    @Column(name = "branch_id", nullable = false, updatable = false) private Long branchId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static BusinessSetupAudit created(Long ownerId, Long businessId, Long branchId, Instant now) {
        BusinessSetupAudit value = new BusinessSetupAudit(); value.ownerId = ownerId;
        value.businessId = businessId; value.branchId = branchId; value.createdAt = now; return value;
    }
}
