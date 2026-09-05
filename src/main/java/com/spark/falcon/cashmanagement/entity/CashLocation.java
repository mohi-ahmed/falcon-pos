package com.spark.falcon.cashmanagement.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "cash_locations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cash_location_branch_name", columnNames = {"branch_id", "name"})
}, indexes = {
        @Index(name = "idx_cash_location_business_branch", columnList = "business_id,branch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_location_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CashLocationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashLocationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static CashLocation create(Long businessId, Long branchId, String name,
                                      CashLocationType type, Instant now) {
        CashLocation location = new CashLocation();
        location.businessId = Objects.requireNonNull(businessId, "businessId is required");
        location.branchId = Objects.requireNonNull(branchId, "branchId is required");
        location.name = required(name, "name");
        location.type = Objects.requireNonNull(type, "type is required");
        location.status = CashLocationStatus.ACTIVE;
        location.createdAt = Objects.requireNonNull(now, "now is required");
        location.updatedAt = now;
        return location;
    }

    public boolean isActive() {
        return status == CashLocationStatus.ACTIVE;
    }

    public void changeStatus(CashLocationStatus status, Instant now) {
        this.status = Objects.requireNonNull(status, "status is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
