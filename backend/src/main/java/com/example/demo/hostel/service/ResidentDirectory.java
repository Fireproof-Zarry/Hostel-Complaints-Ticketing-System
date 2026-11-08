package com.example.demo.hostel.service;

/** Whether a student lives in the hostel. AuthService asks this on every student request. */
@FunctionalInterface
public interface ResidentDirectory {

    /** Allows everyone while the resident list is empty. */
    boolean isResident(String email);
}
