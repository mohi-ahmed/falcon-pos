package com.spark.falcon.user.exception;

public class ProtectedUserGroupException extends RuntimeException {
    public ProtectedUserGroupException() {
        super("Owner or Primary Admin groups are protected from editing and deletion");
    }
}
