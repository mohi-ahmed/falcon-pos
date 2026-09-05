package com.spark.falcon.settings.exception;

public class SettingsCodeAlreadyUsedException extends RuntimeException {
    public SettingsCodeAlreadyUsedException(String type, String code) {
        super(type + " code is already in use: " + code);
    }
}
