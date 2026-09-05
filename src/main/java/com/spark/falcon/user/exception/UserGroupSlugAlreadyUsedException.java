package com.spark.falcon.user.exception;

public class UserGroupSlugAlreadyUsedException extends RuntimeException {
    public UserGroupSlugAlreadyUsedException(String slug) {
        super("User group slug is already used: " + slug);
    }
}
