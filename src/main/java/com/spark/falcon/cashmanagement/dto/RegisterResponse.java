package com.spark.falcon.cashmanagement.dto;

import com.spark.falcon.cashmanagement.entity.RegisterStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private String name;
    private String code;
    private RegisterStatus status;
    private Instant createdAt;
}
