package com.spark.falcon.settings.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payment_method_branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_method_branch", columnNames = {"payment_method_id", "branch_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethodBranch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    private Long paymentMethodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_method_branch_method"))
    @Getter(AccessLevel.NONE)
    private PaymentMethod paymentMethodReference;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_method_branch_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaymentMethodBranch assign(Long paymentMethodId, Long branchId, Instant now) {
        PaymentMethodBranch assignment = new PaymentMethodBranch();
        assignment.paymentMethodId = Objects.requireNonNull(paymentMethodId, "paymentMethodId is required");
        assignment.branchId = Objects.requireNonNull(branchId, "branchId is required");
        assignment.active = true;
        assignment.createdAt = Objects.requireNonNull(now, "now is required");
        assignment.updatedAt = now;
        return assignment;
    }

    public void activate(Instant now) {
        this.active = true;
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }
}
