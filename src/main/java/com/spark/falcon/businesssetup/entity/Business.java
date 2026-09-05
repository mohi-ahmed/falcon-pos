package com.spark.falcon.businesssetup.entity;

import com.spark.falcon.businesssetup.entity.enumtype.BusinessType;
import com.spark.falcon.identity.entity.Owner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "businesses", uniqueConstraints = {
        @UniqueConstraint(name = "uk_business_owner", columnNames = "owner_id"),
        @UniqueConstraint(name = "uk_business_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_business_setup_key", columnNames = "setup_idempotency_key")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Business {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private Owner owner;

    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 20) private String code;
    @Enumerated(EnumType.STRING) @Column(name = "business_type", nullable = false, length = 40)
    private BusinessType businessType;
    @Column(nullable = false, length = 160) private String email;
    @Column(nullable = false, length = 32) private String phone;
    @Column(name = "setup_idempotency_key", nullable = false, updatable = false, length = 36)
    private String setupIdempotencyKey;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version private long version;

    public static Business create(Owner owner, String name, String code, BusinessType type,
                                  String email, String phone, String idempotencyKey, Instant now) {
        Business value = new Business();
        value.owner = Objects.requireNonNull(owner);
        value.name = required(name);
        value.code = required(code).toUpperCase(Locale.ROOT);
        value.businessType = Objects.requireNonNull(type);
        value.email = required(email).toLowerCase(Locale.ROOT);
        value.phone = required(phone);
        value.setupIdempotencyKey = required(idempotencyKey);
        value.createdAt = Objects.requireNonNull(now);
        return value;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required value is missing");
        return value.trim();
    }
}
