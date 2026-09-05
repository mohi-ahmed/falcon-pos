package com.spark.falcon.settings.dto.command;

import java.util.Set;
import com.spark.falcon.settings.entity.enumtype.ConfigurationStatus;

public record CreateUnitCommand(
        Long ownerId, String name, String code, String description, int displayOrder, Set<Long> branchIds, ConfigurationStatus status) {
}
