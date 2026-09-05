package com.spark.falcon.purchase.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StockImportReverseRequest {
    @NotBlank
    @Size(max = 240)
    private String reason;
}
