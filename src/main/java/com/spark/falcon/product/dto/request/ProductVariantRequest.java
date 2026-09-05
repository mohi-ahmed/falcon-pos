package com.spark.falcon.product.dto.request;

import com.spark.falcon.product.entity.enumtype.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ProductVariantRequest {
    @NotBlank @Size(max = 120)
    private String variantName;

    @NotBlank @Size(max = 60)
    private String sku;

    @NotNull @Positive
    private Long baseInventoryUnitId;

    @Positive
    private Long purchaseUnitId;

    @Positive
    private Long sellingUnitId;

    @NotNull @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal sellingPrice;

    @NotNull @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal reorderLevel;

    @NotNull
    private ProductStatus status = ProductStatus.ACTIVE;
}
