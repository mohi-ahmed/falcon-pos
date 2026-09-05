package com.spark.falcon.customer.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        Long businessId,
        String name,
        String phone,
        String email,
        String gender,
        LocalDate dateOfBirth,
        Integer age,
        String address,
        String city,
        String stateDivision,
        String country,
        String notes,
        boolean active,
        boolean archived,
        boolean systemControlled,
        boolean dueEligible,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt
) {
}
