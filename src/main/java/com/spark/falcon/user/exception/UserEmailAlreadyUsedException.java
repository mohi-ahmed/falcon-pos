package com.spark.falcon.user.exception;

public class UserEmailAlreadyUsedException extends RuntimeException {
    public UserEmailAlreadyUsedException(String email) {
        super("A user with email " + email + " already exists in this business");
    }
}
