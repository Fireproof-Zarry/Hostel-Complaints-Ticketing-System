package com.example.demo.hostel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async, so push notifications are sent off the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
