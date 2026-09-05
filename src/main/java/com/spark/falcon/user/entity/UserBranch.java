package com.spark.falcon.user.entity;

import com.spark.falcon.branch.entity.Branch;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "user_branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_branch", columnNames = {"user_id", "branch_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_branch_user"))
    @Getter(AccessLevel.NONE)
    private User userReference;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_branch_branch"))
    @Getter(AccessLevel.NONE)
    private Branch branchReference;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserBranch assign(Long userId, Long branchId, Instant now) {
        UserBranch assignment = new UserBranch();
        assignment.userId = Objects.requireNonNull(userId, "userId is required");
        assignment.branchId = Objects.requireNonNull(branchId, "branchId is required");
        assignment.active = true;
        assignment.createdAt = Objects.requireNonNull(now, "now is required");
        assignment.updatedAt = now;
        return assignment;
    }

    public void activate(Instant now) {
        active = true;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = Objects.requireNonNull(now, "now is required");
    }
}
