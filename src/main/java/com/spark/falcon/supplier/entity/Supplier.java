package com.spark.falcon.supplier.entity;

import com.spark.falcon.businesssetup.entity.Business;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "suppliers", indexes = {
        @Index(name = "idx_supplier_business_name", columnList = "business_id,name"),
        @Index(name = "idx_supplier_business_active", columnList = "business_id,active")
})
@Getter
@Setter
@NoArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_supplier_business"))
    private Business businessReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "code_name", length = 60)
    private String codeName;

    @Column(length = 160)
    private String email;

    @Column(name = "mobile_number", length = 32)
    private String mobileNumber;

    @Column(length = 500)
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String country;

    @Column(name = "additional_details", length = 1000)
    private String additionalDetails;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
