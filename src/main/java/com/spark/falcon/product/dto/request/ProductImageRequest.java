package com.spark.falcon.product.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class ProductImageRequest {
    @Size(max = 500) private String imageReference;
    @Min(0) private int displayOrder;
    private boolean primaryImage;
}
