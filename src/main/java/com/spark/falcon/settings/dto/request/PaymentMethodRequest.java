package com.spark.falcon.settings.dto.request;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@NoArgsConstructor
public class PaymentMethodRequest {
    @NotBlank @Size(max = 120) private String name;
    @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Use letters, numbers, hyphens and underscores only")
    private String code;
    @Size(max = 240) private String description;
    private boolean cash;
    private boolean transactionReferenceRequired;
    @Size(max = 120) private String reconciliationChannelReference;
    @Size(max = 120) private String reconciliationAccountReference;
    @Min(0) private int displayOrder;
    @NotEmpty private Set<Long> branchIds = new LinkedHashSet<>();
    @NotNull private ConfigurationStatus status = ConfigurationStatus.ACTIVE;
}
