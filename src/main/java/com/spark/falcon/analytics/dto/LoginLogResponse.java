package com.spark.falcon.analytics.dto;

import java.time.Instant;

public record LoginLogResponse(
        Long id,
        String username,
        String ipAddress,
        Instant loginAt,
        String eventType,
        String loginStatus
) { }
