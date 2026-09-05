package com.spark.falcon.branch.entity;

import com.spark.falcon.branch.entity.enumtype.BranchStatus;
import com.spark.falcon.businesssetup.entity.Business;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "branches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_business_code", columnNames = {"business_id", "code"}),
        @UniqueConstraint(name = "uk_branch_business_create_key", columnNames = {"business_id", "create_idempotency_key"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    /*
     * Infrastructure-only association used to enforce the database foreign key while
     * the branch domain continues to expose only businessId across module boundaries.
     * The scalar businessId remains the owning value for inserts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_branch_business"))
    @Getter(AccessLevel.NONE)
    private Business businessReference;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 240)
    private String address;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "state_division", length = 80)
    private String stateDivision;

    @Column(name = "postal_code", length = 16)
    private String postalCode;

    @Column(name = "vat_bin_number", length = 40)
    private String vatBinNumber;

    @Column(name = "default_tax_rate", precision = 5, scale = 2)
    private BigDecimal defaultTaxRate;

    @Column(name = "low_stock_alert_quantity", nullable = false)
    private Integer lowStockAlertQuantity;

    @Column(name = "records_per_page", nullable = false)
    private Integer recordsPerPage;

    @Column(name = "receipt_footer", length = 240)
    private String receiptFooter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'ACTIVE'")
    private BranchStatus status;

    // Nullable at database level for legacy setup rows; all new branches require it in the domain factory.
    @Column(name = "create_idempotency_key", updatable = false, length = 36)
    private String createIdempotencyKey;

    @Column(name = "created_by_owner_id", updatable = false)
    private Long createdByOwnerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Branch create(Long businessId, Long ownerId, String idempotencyKey, String name, String code,
                                String country, String email, String phone, String timeZone, String currency,
                                String address, String city, String stateDivision, String postalCode,
                                String vatBinNumber, BigDecimal defaultTaxRate, Integer lowStockAlertQuantity,
                                Integer recordsPerPage, String receiptFooter, Instant now) {
        Branch branch = new Branch();
        branch.businessId = Objects.requireNonNull(businessId, "businessId is required");
        branch.createdByOwnerId = Objects.requireNonNull(ownerId, "ownerId is required");
        branch.createIdempotencyKey = required(idempotencyKey, "idempotencyKey");
        branch.name = required(name, "name");
        branch.code = required(code, "code").toUpperCase(Locale.ROOT);
        branch.country = required(country, "country").toUpperCase(Locale.ROOT);
        branch.email = required(email, "email").toLowerCase(Locale.ROOT);
        branch.phone = required(phone, "phone");
        branch.timeZone = required(timeZone, "timeZone");
        branch.currency = required(currency, "currency").toUpperCase(Locale.ROOT);
        branch.address = required(address, "address");
        branch.city = required(city, "city");
        branch.stateDivision = optional(stateDivision);
        branch.postalCode = optional(postalCode);
        branch.vatBinNumber = optional(vatBinNumber);
        branch.defaultTaxRate = defaultTaxRate;
        branch.lowStockAlertQuantity = Objects.requireNonNull(lowStockAlertQuantity, "lowStockAlertQuantity is required");
        branch.recordsPerPage = Objects.requireNonNull(recordsPerPage, "recordsPerPage is required");
        branch.receiptFooter = optional(receiptFooter);
        branch.status = BranchStatus.ACTIVE;
        branch.createdAt = Objects.requireNonNull(now, "now is required");
        return branch;
    }

    public void updateSettingsProfile(String name, String code, String country, String email, String phone,
                                      String timeZone, String address, String city, String stateDivision,
                                      String postalCode, String vatBinNumber, BigDecimal defaultTaxRate,
                                      Integer lowStockAlertQuantity, Integer recordsPerPage, String receiptFooter) {
        this.name = required(name, "name");
        this.code = required(code, "code").toUpperCase(Locale.ROOT);
        this.country = required(country, "country").toUpperCase(Locale.ROOT);
        this.email = required(email, "email").toLowerCase(Locale.ROOT);
        this.phone = required(phone, "phone");
        this.timeZone = required(timeZone, "timeZone");
        this.address = required(address, "address");
        this.city = required(city, "city");
        this.stateDivision = optional(stateDivision);
        this.postalCode = optional(postalCode);
        this.vatBinNumber = optional(vatBinNumber);
        this.defaultTaxRate = defaultTaxRate;
        this.lowStockAlertQuantity = Objects.requireNonNull(lowStockAlertQuantity, "lowStockAlertQuantity is required");
        this.recordsPerPage = Objects.requireNonNull(recordsPerPage, "recordsPerPage is required");
        this.receiptFooter = optional(receiptFooter);
    }


    public void activate() {
        this.status = BranchStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = BranchStatus.INACTIVE;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
