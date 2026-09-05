package com.spark.falcon.shared.export;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ExportExceptionHandler {
    @ExceptionHandler(InvalidExportFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidExportRequest(InvalidExportFormatException exception) {
        return Map.of("error", exception.getMessage());
    }
}
