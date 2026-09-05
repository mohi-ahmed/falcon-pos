package com.spark.falcon.branch.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class UpdateBranchRequest {
    @NotBlank @Size(max = 120) private String name;
    @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9-]+", message = "Use letters, numbers and hyphens only")
    private String code;
    @NotBlank @Size(min = 2, max = 2) private String country;
    @NotBlank @Email @Size(max = 160) private String email;
    @NotBlank @Size(max = 32) private String phone;
    @NotBlank @Size(max = 64) private String timeZone;
    @NotBlank @Size(max = 240) private String address;
    @NotBlank @Size(max = 80) private String city;
    @Size(max = 80) private String stateDivision;
    @Size(max = 16) private String postalCode;
    @Size(max = 40) private String vatBinNumber;
    @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
    private BigDecimal defaultTaxRate;
    @NotNull @Min(0) @Max(999999) private Integer lowStockAlertQuantity;
    @NotNull private Integer recordsPerPage;
    @Size(max = 240) private String receiptFooter;
}
