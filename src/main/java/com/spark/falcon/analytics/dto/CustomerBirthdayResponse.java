package com.spark.falcon.analytics.dto;

import java.time.LocalDate;

public record CustomerBirthdayResponse(
        Long customerId,
        String customerName,
        LocalDate dateOfBirth,
        LocalDate nextBirthday,
        long daysUntilBirthday,
        long memberDays
) { }
