package com.spark.falcon.businesssetup.exception;
public class BusinessCodeAlreadyUsedException extends RuntimeException {
    public BusinessCodeAlreadyUsedException() { super("Business code is already in use"); }
}
