package com.spark.falcon.user.exception;

public class PermissionNotFoundException extends RuntimeException {
    public PermissionNotFoundException() {
        super("One or more selected permissions were not found");
    }
}
