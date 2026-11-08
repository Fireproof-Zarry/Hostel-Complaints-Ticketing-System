# Complaint Status Transition Rules

| | |
|---|---|
| **Goal** | Enforce valid complaint status transitions in the backend. |
| **Date** | 2026-10-05 |
| **Pull requests** | #7 |
| **Commits** | `ccd84f1` (merge) |

## Context

Complaint statuses needed a controlled workflow. Without explicit transition rules, administrators could potentially move complaints between statuses in invalid ways.

## Prompt

```text
Implement complaint status transition rules in the backend.

Create a dedicated ComplaintTransitionService to validate status changes.

Allow only these transitions:

- PENDING -> ASSIGNED
- PENDING -> REJECTED
- ASSIGNED -> IN_PROGRESS
- IN_PROGRESS -> RESOLVED

Reject invalid transitions with an IllegalArgumentException.

Integrate the validation into AdminService.updateStatus and expose the functionality through:

PATCH /api/admin/complaints/{id}/status

Return HTTP 400 for invalid transitions.

Add tests for the transition rules, service logic, and controller endpoint.