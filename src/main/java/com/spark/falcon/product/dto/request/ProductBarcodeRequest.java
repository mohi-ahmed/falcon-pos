package com.spark.falcon.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProductBarcodeRequest {
    @NotBlank @Size(max = 120)
    private String barcode;

    @NotNull @Positive
    private Long unitId;
}
