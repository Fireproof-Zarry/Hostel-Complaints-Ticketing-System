package com.example.demo.hostel.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Logs why a bearer token was rejected (e.g. "Jwt expired at ...", "The iss
 * claim is not valid", or our own account checks), then responds exactly like
 * Spring's BearerTokenAuthenticationEntryPoint (401 + WWW-Authenticate).
 * The token itself is never logged.
 */
public class LoggingBearerTokenEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(LoggingBearerTokenEntryPoint.class);

    private final AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    /** Logs why the token was rejected, then sends the usual 401. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            log.warn("Rejected bearer token for {} {}: {}", request.getMethod(), request.getRequestURI(),
                    describe(authException));
        } else {
            log.debug("No bearer token for {} {}", request.getMethod(), request.getRequestURI());
        }
        delegate.commence(request, response, authException);
    }

    private static String describe(Throwable error) {
        StringBuilder description = new StringBuilder(error.getClass().getSimpleName())
                .append(": ").append(error.getMessage());
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            description.append(" (cause: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage()).append(')');
        }
        return description.toString();
    }
}
