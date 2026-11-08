# Status workflow and history API

| | |
|---|---|
| **Goal** | Reject invalid status changes, and expose the complaint history to students and admins. |
| **Date** | 2026-11-05 |
| **Pull requests** | #30 |
| **Commits** | `bb6f8c3` |

## Context

Any status change was allowed. The prompt asked for the transition rules to be checked against the work-order code before implementing.

## Prompt

```text
Context: We need to expose the complaint_history timeline to students and admins, and enforce strict state-machine rules for complaint status transitions in the HostelFix Spring Boot backend.

Task 1: Architecture & State Verification (DO THIS FIRST)
Review the existing ComplaintTransitionService.java (if it exists) and the complaint_history table schema. Check if we already have logic logging these transitions. Also, verify what the valid status enums are (e.g., PENDING, IN_PROGRESS, RESOLVED, REJECTED). If there are any missing constraints or if the transition logic conflicts with Nikhitha's new WorkOrderService, point it out before writing new code. If everything is sound, proceed to implementation.

Task 2: State Machine Enforcement
Update the backend transition logic (in ComplaintTransitionService or ComplaintService) to strictly reject invalid status changes.
Rules:

PENDING -> IN_PROGRESS or REJECTED.

IN_PROGRESS -> RESOLVED.

Throw an IllegalStateException or custom API exception if an admin or API call attempts to bypass the flow (e.g., jumping from PENDING directly to RESOLVED without being IN_PROGRESS).

Task 3: History API Endpoint
Create GET /api/complaints/{id}/history.
Constraints:

The endpoint must return a list of complaint_history records, ordered by created_at descending.

Implement authorization: A student can ONLY fetch the history if the complaint belongs to their user_id. Admins/In-charges can fetch the history for any complaint in their authorized territory.
```

## Constraints

- PENDING to IN_PROGRESS or REJECTED, and IN_PROGRESS to RESOLVED
- Students only see their own complaint's history

## Refinements

- **The rules as written would have broken work orders,** so nothing was changed until we decided. They left out `ASSIGNED`, gave nothing a way back to Pending, and meant a started complaint could never be rejected.
- **We settled the rules:** a small job can go straight from Assigned to Resolved, closed complaints can be reopened to Pending or In Progress, and In Progress can go back to Assigned to undo a mistake.
- **A 409 instead of `IllegalStateException`,** which would have surfaced as a server error.
- **Reopening resumes the last work order,** or unassigns the complaint if that person has left the staff.
- **History is newest first,** and admins and staff can read any complaint's history with real names.

## Outcome

Every pair of statuses is tested against one table. The Postgres-only tests that assumed the old rules were updated by hand, because they only run in CI.
