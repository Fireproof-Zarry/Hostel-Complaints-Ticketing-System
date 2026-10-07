package com.example.demo.hostel.config;

import com.example.demo.hostel.model.User;
import com.example.demo.hostel.service.AuthService;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.core.convert.converter.Converter;

@Component
public class CustomJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthService authService;

    public CustomJwtAuthenticationConverter(AuthService authService) {
        this.authService = authService;
    }

    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = authService.processGoogleLogin(jwt);

        return new JwtAuthenticationToken(
                jwt,
                java.util.List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + user.getRole().name()
                        )
                )
        );
    }
}