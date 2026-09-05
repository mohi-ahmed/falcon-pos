package com.spark.falcon.product.dto.request;
import com.spark.falcon.product.entity.enumtype.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.LinkedHashSet;
import java.util.Set;
@Data
public class CategoryRequest {
    @NotBlank @Size(max = 120) private String name;
    @Size(max = 140) private String slug;
    @Positive private Long parentCategoryId;
    @Size(max = 1000) private String description;
    @Size(max = 500) private String thumbnailReference;
    @Min(0) private int displayOrder;
    @NotNull private ProductStatus status = ProductStatus.ACTIVE;
    @NotEmpty private Set<@NotNull @Positive Long> branchIds = new LinkedHashSet<>();
}
