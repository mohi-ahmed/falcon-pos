package com.spark.falcon.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_reference_sequences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReferenceSequence {

    @Id
    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Column(name = "last_issued_number", nullable = false)
    private long lastIssuedNumber;
}
