package com.spark.falcon.user.dto.command;

import com.spark.falcon.user.entity.enumtype.ManagementActorType;

public record ManagementActor(Long businessId, ManagementActorType type, Long actorId) {
}
