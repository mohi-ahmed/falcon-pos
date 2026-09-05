package com.spark.falcon.settings.dto.request;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class TaxRateRequest {
    @NotBlank @Size(max = 120) private String name;
    @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Use letters, numbers, hyphens and underscores only")
    private String code;
    @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") @Digits(integer = 3, fraction = 4)
    private BigDecimal rate;
    @Min(0) private int displayOrder;
    @NotNull private ConfigurationStatus status = ConfigurationStatus.ACTIVE;
}
