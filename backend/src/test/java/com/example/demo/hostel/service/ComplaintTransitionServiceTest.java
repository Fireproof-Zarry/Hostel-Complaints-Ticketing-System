package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
    void pendingToResolved_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.PENDING,
                        Status.RESOLVED
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
    void assignedToPending_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.ASSIGNED,
                        Status.PENDING
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
    void inProgressToRejected_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.IN_PROGRESS,
                        Status.REJECTED
                )
        );
    }

    @Test
    void resolvedToPending_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.RESOLVED,
                        Status.PENDING
                )
        );
    }

    @Test
    void resolvedToInProgress_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.RESOLVED,
                        Status.IN_PROGRESS
                )
        );
    }

    @Test
    void rejectedToAssigned_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.REJECTED,
                        Status.ASSIGNED
                )
        );
    }

    @Test
    void rejectedToResolved_isAllowed() {
        assertDoesNotThrow(() ->
                transitionService.validateTransition(
                        Status.REJECTED,
                        Status.RESOLVED
                )
        );
    }
}