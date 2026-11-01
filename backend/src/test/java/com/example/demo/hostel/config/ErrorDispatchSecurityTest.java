package com.example.demo.hostel.config;

import com.example.demo.hostel.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Runs a real server, because MockMvc does not perform Spring's /error
 * re-dispatch. A failure inside the login check must surface as a 500, not as
 * a 401 that the frontend would treat as a rejected sign-in (and log out).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:error-dispatch-security-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ErrorDispatchSecurityTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthService authService;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void failureDuringTheLoginCheckIsA500NotA401() throws Exception {
        when(jwtDecoder.decode(eq("valid-token"))).thenReturn(validJwt());
        when(authService.processGoogleLogin(any())).thenThrow(new DataAccessResourceFailureException("database hiccup"));

        HttpResponse<String> response = get("/api/complaints/mine", "valid-token");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @Test
    void invalidTokenIsStillA401WithInvalidTokenError() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Jwt expired at 2026-11-01T10:00:00Z"));

        HttpResponse<String> response = get("/api/complaints/mine", "expired-token");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse(""))
                .contains("error=\"invalid_token\"")
                .contains("Jwt expired");
    }

    @Test
    void missingTokenIsStillA401() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/complaints/mine")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Authorization", "Bearer " + token)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Jwt validJwt() {
        return Jwt.withTokenValue("valid-token")
                .header("alg", "none")
                .claim("email", "student@smail.iitm.ac.in")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
