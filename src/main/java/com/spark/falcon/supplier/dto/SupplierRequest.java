package com.spark.falcon.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@NoArgsConstructor
public class SupplierRequest {

    @NotBlank(message = "Supplier name is required")
    @Size(max = 120)
    private String name;

    @Size(max = 60)
    private String codeName;

    @Email(message = "Enter a valid supplier email")
    @Size(max = 160)
    private String email;

    @Size(max = 32)
    private String mobileNumber;

    @Size(max = 500)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 100)
    private String country;

    @Size(max = 1000)
    private String additionalDetails;

    private boolean active = true;

    @Min(value = 0, message = "Display order cannot be negative")
    private int displayOrder;

    @NotEmpty(message = "Select at least one branch")
    private Set<Long> branchIds = new LinkedHashSet<>();
}
