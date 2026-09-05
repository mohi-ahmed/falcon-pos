package com.spark.falcon.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "password_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PasswordHistoryEntry create(
            Owner owner,
            String passwordHash,
            Instant now
    ) {
        PasswordHistoryEntry entry = new PasswordHistoryEntry();

        entry.owner = Objects.requireNonNull(owner, "owner");
        entry.passwordHash = requireText(passwordHash, "passwordHash");
        entry.createdAt = Objects.requireNonNull(now, "now");

        return entry;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value;
    }
}