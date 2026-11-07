package com.example.demo.hostel.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Who can reach what. Every request carries a Google ID token, checked by the resource
 * server and turned into a role by CustomJwtAuthenticationConverter. The public routes are
 * /health and POST /api/auth/login; /api/admin/staff is for admins; the rest of /api/admin
 * and /api/work-orders are for admins and staff; anything else needs a valid sign-in.
 */
@Configuration
public class SecurityConfig {

    private final CustomJwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(
            CustomJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    /** The URL rules, token handling and complaint rate limit, for every request. */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ComplaintRateLimiter complaintRateLimiter)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Spring renders errors by re-dispatching to /error. If the login check
                // itself fails (e.g. a database error), that dispatch has no login and
                // the real 500 would turn into a 401, which the frontend treats as a
                // rejected sign-in. Error dispatches only render the original error.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/health").permitAll()
                // The login page's token is verified inside this endpoint
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                // Staff management is admin-only; the rest of the admin area
                // (complaints, archive, assignment) is shared with hostel staff
                .requestMatchers("/api/admin/staff/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "STAFF")
                .requestMatchers("/api/work-orders/**").hasAnyRole("ADMIN", "STAFF")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                // Same 401 response, plus a log line with the exact rejection reason
                .authenticationEntryPoint(new LoggingBearerTokenEntryPoint())
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
            );

        // After authentication, so the limit is per signed-in user
        http.addFilterAfter(new ComplaintRateLimitFilter(complaintRateLimiter), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    // Declared here so every test that imports SecurityConfig gets it too
    @Bean
    public ComplaintRateLimiter complaintRateLimiter() {
        return new ComplaintRateLimiter();
    }

    /** Lets the local and deployed frontends call the API, and read the headers they need. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "https://hostel-complaints-frontend.onrender.com"
        ));

        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
            List.of("Authorization", "Content-Type")
        );

        // Readable by the frontend: why a token was rejected ("Jwt expired at ..."),
        // and Retry-After, which marks a 429 as the complaint-filing rate limit
        configuration.setExposedHeaders(List.of("WWW-Authenticate", "Retry-After"));

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}