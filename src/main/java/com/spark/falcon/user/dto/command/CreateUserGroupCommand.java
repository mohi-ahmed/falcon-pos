package com.spark.falcon.user.dto.command;

public record CreateUserGroupCommand(ManagementActor actor, String name, String slug) {
}
