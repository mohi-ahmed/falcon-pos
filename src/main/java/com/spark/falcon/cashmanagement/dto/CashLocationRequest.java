package com.spark.falcon.cashmanagement.dto;

import com.spark.falcon.cashmanagement.entity.CashLocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CashLocationRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    private CashLocationType type;
}
