package com.spark.falcon.identity.dto;

public record RegisterOwnerCommand(
        String fullName,
        String email,
        String rawPassword,
        String mobileNumber
) {
}

