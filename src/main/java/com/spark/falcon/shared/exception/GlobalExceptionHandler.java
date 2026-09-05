package com.spark.falcon.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleMissingResource() {
        // Expected 404 response. Do not pollute production error logs.
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpectedException(
            Exception exception,
            HttpServletRequest request,
            Model model
    ) {
        log.error("Unhandled request failure: method={}, path={}",
                request.getMethod(), request.getRequestURI(), exception);
        model.addAttribute("requestPath", request.getRequestURI());
        return "error/500";
    }
}
