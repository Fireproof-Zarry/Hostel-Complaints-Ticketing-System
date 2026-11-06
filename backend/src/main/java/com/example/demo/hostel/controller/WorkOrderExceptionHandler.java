package com.example.demo.hostel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * The work-order, staff and login rules reject requests with reasons written for the
 * user ("Staff can only assign complaints to themselves"). Spring's default
 * error body drops the reason, so return it as JSON the frontend can show.
 */
@RestControllerAdvice(assignableTypes = {
        AdminController.class, AuthController.class, StaffController.class, WorkOrderController.class})
public class WorkOrderExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String error = status != null ? status.getReasonPhrase() : "Error";
        String message = e.getReason() != null ? e.getReason() : error;
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", error, "message", message));
    }
}
