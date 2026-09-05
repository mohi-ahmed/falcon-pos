package com.spark.falcon.expense.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class ExpenseCategoryRequest {
    @NotBlank @Size(max=120) private String name;
    @Size(max=140) @Pattern(regexp="^$|[a-z0-9]+(?:-[a-z0-9]+)*$") private String slug;
    private Long parentCategoryId;
    @Size(max=1000) private String description;
    private boolean active = true;
    @Min(0) private int displayOrder;
}
