package com.example.demo.hostel.config;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Limits POST /api/complaints to ComplaintRateLimiter.CAPACITY requests per
 * minute per signed-in user (their email from the JWT), falling back to the
 * client IP. Runs after authentication so the user is known. Exceeding it
 * returns 429 with Retry-After, which tells it apart from the existing 429
 * for "too many open complaints" (that one has no Retry-After).
 */
public class ComplaintRateLimitFilter extends OncePerRequestFilter {

    private final ComplaintRateLimiter rateLimiter;

    public ComplaintRateLimitFilter(ComplaintRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equalsIgnoreCase(request.getMethod())
                && ("/api/complaints".equals(path) || "/api/complaints/".equals(path)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ConsumptionProbe probe = rateLimiter.tryConsume(clientKey(request));
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many complaints filed in a short time. "
                + "Try again in " + retryAfterSeconds + " seconds.\",\"retryAfterSeconds\":" + retryAfterSeconds + "}");
    }

    private static String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String email = jwtAuthentication.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return "user:" + email.trim().toLowerCase(Locale.ROOT);
            }
        }
        // Behind Render's proxy this is the real client IP (server.forward-headers-strategy)
        return "ip:" + request.getRemoteAddr();
    }
}
