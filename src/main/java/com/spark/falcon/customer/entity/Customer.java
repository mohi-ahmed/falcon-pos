package com.spark.falcon.customer.entity;

import com.spark.falcon.businesssetup.entity.Business;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "customers", indexes = {
        @Index(name = "idx_customer_business_name", columnList = "business_id,name"),
        @Index(name = "idx_customer_business_phone", columnList = "business_id,normalized_phone"),
        @Index(name = "idx_customer_business_email", columnList = "business_id,normalized_email"),
        @Index(name = "idx_customer_business_archived", columnList = "business_id,archived_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_customer_business"))
    private Business businessReference;

    @Column(length = 120)
    private String name;

    @Column(length = 32)
    private String phone;

    @Column(name = "normalized_phone", length = 32)
    private String normalizedPhone;

    @Column(length = 160)
    private String email;

    @Column(name = "normalized_email", length = 160)
    private String normalizedEmail;

    @Column(length = 32)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private Integer age;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(name = "state_division", length = 100)
    private String stateDivision;

    @Column(length = 100)
    private String country;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "system_controlled", nullable = false)
    private boolean systemControlled;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isEligibleForDueSale() {
        return active && archivedAt == null && !systemControlled;
    }
}
