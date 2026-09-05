package com.spark.falcon.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.stereotype.Component;

@Component
public class FalconAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, FalconWebAuthenticationDetails> {
    @Override
    public FalconWebAuthenticationDetails buildDetails(HttpServletRequest context) {
        return new FalconWebAuthenticationDetails(context);
    }
}
