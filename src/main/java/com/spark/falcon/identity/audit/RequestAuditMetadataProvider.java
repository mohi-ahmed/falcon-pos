package com.spark.falcon.identity.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestAuditMetadataProvider {

    public AuditRequestMetadata current() {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return AuditRequestMetadata.empty();
        }

        HttpServletRequest request = attributes.getRequest();

        return new AuditRequestMetadata(
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
    }

    public record AuditRequestMetadata(
            String ipAddress,
            String userAgent
    ) {

        public static AuditRequestMetadata empty() {
            return new AuditRequestMetadata(null, null);
        }
    }
}