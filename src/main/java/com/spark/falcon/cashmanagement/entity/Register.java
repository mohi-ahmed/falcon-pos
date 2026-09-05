package com.spark.falcon.cashmanagement.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "cash_registers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cash_register_branch_code", columnNames = {"branch_id", "code"})
}, indexes = {
        @Index(name = "idx_cash_register_business_branch", columnList = "business_id,branch_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Register {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_cash_register_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegisterStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Register create(Long businessId, Long branchId, String name, String code, Instant now) {
        Register register = new Register();
        register.businessId = Objects.requireNonNull(businessId, "businessId is required");
        register.branchId = Objects.requireNonNull(branchId, "branchId is required");
        register.name = required(name, "name");
        register.code = required(code, "code");
        register.status = RegisterStatus.ACTIVE;
        register.createdAt = Objects.requireNonNull(now, "now is required");
        register.updatedAt = now;
        return register;
    }

    public boolean isActive() {
        return status == RegisterStatus.ACTIVE;
    }

    public void changeStatus(RegisterStatus status, Instant now) {
        this.status = Objects.requireNonNull(status, "status is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
