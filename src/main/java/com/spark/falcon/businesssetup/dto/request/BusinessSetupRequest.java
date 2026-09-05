package com.spark.falcon.businesssetup.dto.request;

import com.spark.falcon.businesssetup.entity.enumtype.BusinessType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class BusinessSetupRequest {
    @NotBlank @Size(max = 36) private String idempotencyKey;
    @NotBlank @Size(max = 120) private String businessName;
    @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9-]+") private String businessCode;
    @NotNull private BusinessType businessType;
    @NotBlank @Email @Size(max = 160) private String businessEmail;
    @NotBlank @Size(max = 32) private String businessPhone;
    @NotBlank @Size(max = 120) private String branchName;
    @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9-]+") private String branchCode;
    @NotBlank @Email @Size(max = 160) private String branchEmail;
    @NotBlank @Size(max = 32) private String branchPhone;
    @NotBlank @Size(min = 2, max = 2) private String country;
    @NotBlank @Size(max = 64) private String timezone;
    @NotBlank @Size(min = 3, max = 3) private String currency;
    @NotBlank @Size(max = 240) private String address;
    @NotBlank @Size(max = 80) private String city;
    @Size(max = 80) private String stateDivision;
    @Size(max = 16) private String postalCode;
    @Size(max = 40) private String vatBinNumber;
    @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
    private BigDecimal defaultTaxRate;
    @NotNull @Min(0) @Max(999999) private Integer lowStockAlertQuantity = 10;
    @NotNull private Integer recordsPerPage = 25;
    @Size(max = 240) private String receiptFooter;
    @AssertTrue(message = "Please confirm the business details before continuing")
    private boolean confirmation;
}
