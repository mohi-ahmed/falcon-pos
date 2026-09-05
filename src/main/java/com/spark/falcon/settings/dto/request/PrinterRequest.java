package com.spark.falcon.settings.dto.request;

import com.spark.falcon.settings.entity.enumtype.PrinterConnectionType;
import com.spark.falcon.settings.entity.enumtype.PrinterType;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@NoArgsConstructor
public class PrinterRequest {
    @NotBlank @Size(max = 120) private String title;
    @NotNull private PrinterType printerType;
    @NotNull private PrinterConnectionType connectionType;
    @Min(1) @Max(512) private int charactersPerLine;
    @Size(max = 240) private String printerPath;
    @Size(max = 64) private String ipAddress;
    @Min(1) @Max(65535) private Integer port;
    @Min(0) private int displayOrder;
    @NotEmpty private Set<Long> branchIds = new LinkedHashSet<>();
    @NotNull private ConfigurationStatus status = ConfigurationStatus.ACTIVE;
}
