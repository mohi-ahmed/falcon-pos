package com.spark.falcon.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("User was not found");
    }
}
