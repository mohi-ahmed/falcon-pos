package com.spark.falcon.cashmanagement.dto;

import com.spark.falcon.cashmanagement.entity.CashLocationStatus;
import com.spark.falcon.cashmanagement.entity.CashLocationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashLocationResponse {
    private Long id;
    private Long businessId;
    private Long branchId;
    private String name;
    private CashLocationType type;
    private CashLocationStatus status;
    private Instant createdAt;
}
