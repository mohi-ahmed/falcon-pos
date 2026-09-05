package com.spark.falcon.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class FalconWebAuthenticationDetails extends WebAuthenticationDetails {
    private final String businessCode;

    public FalconWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        String value = request.getParameter("businessCode");
        this.businessCode = value == null ? null : value.trim();
    }

    public String businessCode() {
        return businessCode;
    }
}
