package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Complaint;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.LocalDateTime;

/**
 * A complaint as listed for its owner: the usual complaint fields plus who to
 * contact while it has an active work order (assignee is null otherwise), and
 * whether it can be nudged now or from when (nextNudgeAt is null once closed).
 */
public record MyComplaint(
        @JsonUnwrapped Complaint complaint,
        AssigneeContact assignee,
        boolean canNudge,
        LocalDateTime nextNudgeAt) {
}
