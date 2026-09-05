package com.spark.falcon.user.entity;

import com.spark.falcon.businesssetup.entity.Business;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "user_groups", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_group_business_slug", columnNames = {"business_id", "slug"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_group_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 140)
    private String slug;

    @Column(name = "protected_group", nullable = false)
    private boolean protectedGroup;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static UserGroup create(Long businessId, String name, String slug, Instant now) {
        UserGroup group = new UserGroup();
        group.businessId = Objects.requireNonNull(businessId, "businessId is required");
        group.name = required(name, "name");
        group.slug = required(slug, "slug");
        group.protectedGroup = isReservedProtectedSlug(group.slug);
        group.createdAt = Objects.requireNonNull(now, "now is required");
        group.updatedAt = now;
        return group;
    }

    public void update(String name, String slug, Instant now) {
        ensureEditable();
        this.name = required(name, "name");
        this.slug = required(slug, "slug");
        this.protectedGroup = isReservedProtectedSlug(this.slug);
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void ensurePermissionsEditable() {
        ensureEditable();
    }

    public void archive(Instant now) {
        ensureEditable();
        if (archivedAt != null) return;
        archivedAt = Objects.requireNonNull(now, "now is required");
        updatedAt = now;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    private void ensureEditable() {
        if (protectedGroup) throw new IllegalStateException("Protected user group cannot be changed");
        if (archivedAt != null) throw new IllegalStateException("Archived user group cannot be changed");
    }

    private static boolean isReservedProtectedSlug(String slug) {
        return "owner".equalsIgnoreCase(slug) || "primary-admin".equalsIgnoreCase(slug);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
