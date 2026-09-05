package com.spark.falcon.customer.dto;

public record CustomerAccessResponse(
        Long id,
        Long businessId,
        String name,
        String phone,
        String email,
        boolean active,
        boolean archived,
        boolean systemControlled,
        boolean dueEligible
) {
}
