package com.spark.falcon.identity.web.support;

import org.springframework.stereotype.Component;

@Component
public class EmailAddressMasker {
    private static final String FALLBACK = "••••••";

    public String mask(String email) {
        if (email == null || email.isBlank()) return FALLBACK;
        int separator = email.indexOf('@');
        if (separator <= 0) return FALLBACK;
        String local = email.substring(0, separator);
        return local.charAt(0) + "•".repeat(Math.max(3, local.length() - 1)) + email.substring(separator);
    }
}
