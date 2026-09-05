package com.spark.falcon.user.exception;

public class UserGroupNotFoundException extends RuntimeException {
    public UserGroupNotFoundException() {
        super("User group was not found");
    }
}
