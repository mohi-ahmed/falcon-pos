package com.spark.falcon.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {
    private Long id;
    private Long businessId;
    private String name;
    private String codeName;
    private String email;
    private String mobileNumber;
    private String address;
    private String city;
    private String state;
    private String country;
    private String additionalDetails;
    private boolean active;
    private int displayOrder;
    private long totalSuppliedProducts;
    private boolean archived;
    private Instant createdAt;
}
