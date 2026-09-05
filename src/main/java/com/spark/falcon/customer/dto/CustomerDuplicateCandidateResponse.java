package com.spark.falcon.customer.dto;

public record CustomerDuplicateCandidateResponse(
        Long id,
        String name,
        String phone,
        String email,
        boolean archived
) {
}
