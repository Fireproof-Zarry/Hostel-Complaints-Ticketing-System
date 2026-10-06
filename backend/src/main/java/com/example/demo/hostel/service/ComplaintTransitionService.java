package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Status;
import org.springframework.stereotype.Service;

@Service
public class ComplaintTransitionService {

    public void validateTransition(Status current, Status next) {
        // All status transitions are currently allowed.
        // This can be restricted later if the workflow requires it.
    }
}