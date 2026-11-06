package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplaintTransitionServiceTest {

    private ComplaintTransitionService transitionService;

    @BeforeEach
    void setUp() {
        transitionService = new ComplaintTransitionService(null, null, null, null);
    }

    /** Every pair of statuses, so a change to the workflow has to change this table too. */
    @Test
    void onlyTheWorkflowsTransitionsAreAllowed() {
        Map<Status, Set<Status>> allowed = new EnumMap<>(Status.class);
        allowed.put(Status.PENDING, EnumSet.of(Status.ASSIGNED, Status.IN_PROGRESS, Status.REJECTED));
        allowed.put(Status.ASSIGNED, EnumSet.of(Status.IN_PROGRESS, Status.RESOLVED, Status.PENDING, Status.REJECTED));
        allowed.put(Status.IN_PROGRESS, EnumSet.of(Status.RESOLVED, Status.ASSIGNED, Status.PENDING, Status.REJECTED));
        allowed.put(Status.RESOLVED, EnumSet.of(Status.PENDING, Status.IN_PROGRESS));
        allowed.put(Status.REJECTED, EnumSet.of(Status.PENDING, Status.IN_PROGRESS));

        for (Status from : Status.values()) {
            for (Status to : Status.values()) {
                if (allowed.get(from).contains(to)) {
                    assertDoesNotThrow(() -> transitionService.validateTransition(from, to), from + " to " + to);
                } else {
                    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                            () -> transitionService.validateTransition(from, to), from + " to " + to);
                    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
                }
            }
        }
    }
}
