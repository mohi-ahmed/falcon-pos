package com.spark.falcon.user.dto.command;

public record UpdateUserGroupCommand(ManagementActor actor, Long userGroupId, String name, String slug) {
}
