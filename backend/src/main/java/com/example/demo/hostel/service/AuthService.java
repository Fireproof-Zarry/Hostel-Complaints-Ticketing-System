package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Role;
import com.example.demo.hostel.model.User;
import com.example.demo.hostel.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    
    private final List<String> adminEmails = List.of("bhadra@smail.iitm.ac.in", "cs23b051@smail.iitm.ac.in", "cs23b103@smail.iitm.ac.in");

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User processGoogleLogin(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String hostedDomain = jwt.getClaimAsString("hd");

        if (hostedDomain == null || !hostedDomain.equals("smail.iitm.ac.in")) {
            throw new IllegalArgumentException("Only smail.iitm.ac.in accounts are allowed.");
        }

        return userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name); 
            
            if (adminEmails.contains(email)) {
                newUser.setRole(Role.ADMIN);
            } else {
                newUser.setRole(Role.STUDENT);
            }
            
            return userRepository.save(newUser);
        });
    }
}