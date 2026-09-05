package com.spark.falcon.identity.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RecoveryCsrfAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler defaultHandler =
            new AccessDeniedHandlerImpl();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {

        if (exception instanceof CsrfException
                && isPasswordRecoveryRequest(request)) {

            response.sendRedirect(
                    request.getContextPath()
                            + "/forgot-password?sessionExpired"
            );

            return;
        }

        defaultHandler.handle(request, response, exception);
    }

    private boolean isPasswordRecoveryRequest(
            HttpServletRequest request
    ) {
        String uri = request.getRequestURI();

        return uri.startsWith(
                request.getContextPath() + "/password-reset"
        );
    }
}