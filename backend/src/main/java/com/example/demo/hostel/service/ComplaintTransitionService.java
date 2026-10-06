package com.example.demo.hostel.service;

import com.example.demo.hostel.model.Status;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class ComplaintTransitionService {

    private final Map<Status, Set<Status>> allowedMoves =
            new EnumMap<>(Status.class);

    public ComplaintTransitionService() {

        allowedMoves.put(
                Status.PENDING,
                EnumSet.of(Status.ASSIGNED, Status.REJECTED)
        );

        allowedMoves.put(
                Status.ASSIGNED,
                EnumSet.of(Status.IN_PROGRESS)
        );

        allowedMoves.put(
                Status.IN_PROGRESS,
                EnumSet.of(Status.RESOLVED)
        );

        allowedMoves.put(
                Status.RESOLVED,
                EnumSet.noneOf(Status.class)
        );

        allowedMoves.put(
                Status.REJECTED,
                EnumSet.noneOf(Status.class)
        );
    }

    public void validateTransition(Status current, Status next) {

        Set<Status> allowed = allowedMoves.getOrDefault(
                current,
                EnumSet.noneOf(Status.class)
        );

        if (!allowed.contains(next)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: "
                            + current + " -> " + next
            );
        }
    }
}