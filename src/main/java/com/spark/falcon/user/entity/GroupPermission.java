package com.spark.falcon.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "group_permissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_permission", columnNames = {"user_group_id", "permission_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_group_id", nullable = false, updatable = false)
    private Long userGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_group_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_group_permission_group"))
    @Getter(AccessLevel.NONE)
    private UserGroup userGroupReference;

    @Column(name = "permission_id", nullable = false, updatable = false)
    private Long permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_group_permission_permission"))
    @Getter(AccessLevel.NONE)
    private Permission permissionReference;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static GroupPermission assign(Long userGroupId, Long permissionId, Instant now) {
        GroupPermission assignment = new GroupPermission();
        assignment.userGroupId = Objects.requireNonNull(userGroupId, "userGroupId is required");
        assignment.permissionId = Objects.requireNonNull(permissionId, "permissionId is required");
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
