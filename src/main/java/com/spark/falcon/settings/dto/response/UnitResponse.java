package com.spark.falcon.settings.dto.response;

import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

import java.util.Set;

public record UnitResponse(
        Long id, Long businessId, String name, String code, String description, int displayOrder,
        ConfigurationStatus status, Set<Long> branchIds, boolean archived) {
}
