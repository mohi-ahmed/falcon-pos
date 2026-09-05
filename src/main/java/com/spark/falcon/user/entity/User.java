package com.spark.falcon.user.entity;

import com.spark.falcon.businesssetup.entity.Business;
import com.spark.falcon.user.entity.enumtype.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_business_email", columnNames = {"business_id", "email"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(name = "user_group_id", nullable = false)
    private Long userGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_group_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_user_group"))
    @Getter(AccessLevel.NONE)
    private UserGroup userGroupReference;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "mobile_number", length = 32)
    private String mobileNumber;

    @Column(name = "password_hash", nullable = false)
    @Getter(AccessLevel.NONE)
    private String passwordHash;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "profile_photo_reference", length = 500)
    private String profilePhotoReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static User create(Long businessId, Long userGroupId, String fullName, String email, String mobileNumber,
                              String passwordHash, LocalDate dateOfBirth, String profilePhotoReference,
                              UserStatus status, Instant now) {
        User user = new User();
        user.businessId = Objects.requireNonNull(businessId, "businessId is required");
        user.userGroupId = Objects.requireNonNull(userGroupId, "userGroupId is required");
        user.fullName = required(fullName, "fullName");
        user.email = normalizeEmail(email);
        user.mobileNumber = optional(mobileNumber);
        user.passwordHash = required(passwordHash, "passwordHash");
        user.dateOfBirth = dateOfBirth;
        user.profilePhotoReference = optional(profilePhotoReference);
        user.status = Objects.requireNonNull(status, "status is required");
        user.createdAt = Objects.requireNonNull(now, "now is required");
        user.updatedAt = now;
        return user;
    }

    public void update(Long userGroupId, String fullName, String email, String mobileNumber,
                       LocalDate dateOfBirth, String profilePhotoReference, UserStatus status, Instant now) {
        ensureNotArchived();
        this.userGroupId = Objects.requireNonNull(userGroupId, "userGroupId is required");
        this.fullName = required(fullName, "fullName");
        this.email = normalizeEmail(email);
        this.mobileNumber = optional(mobileNumber);
        this.dateOfBirth = dateOfBirth;
        this.profilePhotoReference = optional(profilePhotoReference);
        this.status = Objects.requireNonNull(status, "status is required");
        this.updatedAt = Objects.requireNonNull(now, "now is required");
    }

    public void archive(Instant now) {
        if (archivedAt != null) return;
        status = UserStatus.INACTIVE;
        archivedAt = Objects.requireNonNull(now, "now is required");
        updatedAt = now;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isOperationallyActive() {
        return status == UserStatus.ACTIVE && archivedAt == null;
    }

    public String authenticationPasswordHash() {
        return passwordHash;
    }

    private void ensureNotArchived() {
        if (archivedAt != null) {
            throw new IllegalStateException("Archived user cannot be changed");
        }
    }

    private static String normalizeEmail(String value) {
        return required(value, "email").toLowerCase(Locale.ROOT);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


}
