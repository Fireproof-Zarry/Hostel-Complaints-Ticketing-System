package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplaintTransitionServiceTest {

    private ComplaintTransitionService transitionService;

    @BeforeEach
    void setUp() {
        transitionService = new ComplaintTransitionService();
    }

    @Test
    void pendingToAssigned_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.PENDING,
                        Status.ASSIGNED
                )
        );
    }

    @Test
    void pendingToRejected_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.PENDING,
                        Status.REJECTED
                )
        );
    }

    @Test
    void assignedToInProgress_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.ASSIGNED,
                        Status.IN_PROGRESS
                )
        );
    }

    @Test
    void inProgressToResolved_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.IN_PROGRESS,
                        Status.RESOLVED
                )
        );
    }

    @Test
    void pendingToResolved_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> transitionService.validateTransition(
                        Status.PENDING,
                        Status.RESOLVED
                )
        );
    }

    @Test
    void resolvedToPending_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> transitionService.validateTransition(
                        Status.RESOLVED,
                        Status.PENDING
                )
        );
    }

    @Test
    void rejectedToAssigned_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> transitionService.validateTransition(
                        Status.REJECTED,
                        Status.ASSIGNED
                )
        );
    }
}