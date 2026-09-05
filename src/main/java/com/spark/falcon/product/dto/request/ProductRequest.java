package com.spark.falcon.product.dto.request;

import com.spark.falcon.product.entity.enumtype.ExpiryType;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class ProductRequest {
    @NotBlank @Size(max = 120)
    private String name;

    @NotBlank @Size(max = 40)
    private String referenceCode;

    @Size(max = 60)
    private String productType;

    @Positive
    private Long categoryId;

    @Size(max = 100)
    private String brand;

    @Size(max = 40)
    private String barcodeFormat;

    @Size(max = 60)
    private String packagingType;

    private Long taxRateId;

    @Size(max = 40)
    private String taxCalculationMethod;

    @Size(max = 1000)
    private String description;

    @Size(max = 500)
    private String thumbnailReference;

    private boolean trackExpiry;
    private ExpiryType expiryType;
    private boolean batchTrackingRequired;

    @Positive
    private Integer defaultShelfLifeDays;

    @Positive
    private Integer expiryAlertBeforeDays;

    private boolean blockSaleAfterExpiry = true;

    @Min(0)
    private int displayOrder;

    @NotNull
    private ProductStatus status = ProductStatus.ACTIVE;

    @NotEmpty
    private Set<@NotNull @Positive Long> branchIds = new LinkedHashSet<>();

    @Valid
    private List<ProductImageRequest> images = new ArrayList<>();
}
