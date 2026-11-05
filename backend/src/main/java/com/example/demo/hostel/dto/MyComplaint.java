package com.example.demo.hostel.dto;

import com.example.demo.hostel.model.Complaint;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A complaint as listed for its owner: the usual complaint fields plus who to
 * contact while it has an active work order (assignee is null otherwise).
 */
public record MyComplaint(@JsonUnwrapped Complaint complaint, AssigneeContact assignee) {
}
