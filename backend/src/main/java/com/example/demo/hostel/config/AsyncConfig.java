package com.example.demo.hostel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Required for @Async methods (e.g. push notifications) to run off the request thread
@Configuration
@EnableAsync
public class AsyncConfig {
}
