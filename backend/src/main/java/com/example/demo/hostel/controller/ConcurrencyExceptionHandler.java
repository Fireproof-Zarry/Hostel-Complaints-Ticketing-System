package com.example.demo.hostel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Two admins (or an admin and a scheduled job) updated the same complaint at
 * once and this request lost: report a 409 the frontend can show, not a 500.
 */
@RestControllerAdvice
public class ConcurrencyExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "concurrent_update",
                "message", "This complaint was changed by someone else at the same time. Refresh and try again."));
    }
}
