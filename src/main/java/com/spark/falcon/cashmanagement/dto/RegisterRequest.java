package com.spark.falcon.cashmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Size(max = 60)
    private String code;
}
