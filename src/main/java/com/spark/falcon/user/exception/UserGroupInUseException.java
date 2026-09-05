package com.spark.falcon.user.exception;

public class UserGroupInUseException extends RuntimeException {
    public UserGroupInUseException() {
        super("Users are still assigned to this group and must be transferred first");
    }
}
