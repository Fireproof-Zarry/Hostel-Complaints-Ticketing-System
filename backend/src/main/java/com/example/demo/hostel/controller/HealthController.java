package com.example.demo.hostel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public check that the backend is up, for Render and for quick manual tests. */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String checkHealth() {
        return "Backend is up and running";
    }
}