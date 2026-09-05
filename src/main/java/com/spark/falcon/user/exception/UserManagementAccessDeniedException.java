package com.spark.falcon.user.exception;

public class UserManagementAccessDeniedException extends RuntimeException {
    public UserManagementAccessDeniedException() {
        super("You do not have access to manage users for this business");
    }
}
