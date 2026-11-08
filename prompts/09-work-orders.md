# Work orders and hostel staff

| | |
|---|---|
| **Goal** | Let someone take responsibility for a complaint, with a priority and deadline, and introduce hostel staff. |
| **Date** | 2026-11-02 |
| **Pull requests** | #29 |
| **Commits** | `6af6d51`, `f5b5deb`, `08deaba`, `54e0fcf` |

## Context

Admins could only set an assignee's email on a complaint. We asked for the tasks to be verified, with questions, before anything was built.

## Prompt

Sent with this instruction: *verify the tasks before proceeding and ask any questions to clarify any doubts.*

```text
Context: We are building the WorkOrder backend APIs for the HostelFix ticketing system using Spring Boot. Crucial architectural context: The assignee_id in a work order represents a student floor in-charge taking oversight responsibility for a ticket, not an external maintenance worker. The in-charge acts as the dispatcher/manager for their specific hostel territory.

Task 1: Architecture Verification (DO THIS FIRST)
Review the WorkOrder.java entity and the work_orders PostgreSQL table. Verify that the assignee_id maps correctly to the User entity. Ensure there is a logical way to restrict which floor in-charges can oversee which complaints (e.g., verifying their role is IN_CHARGE or ADMIN). If the data model lacks a way to validate this assignment securely, point this out before proceeding.

Task 2: Repository Updates
Create or update WorkOrderRepository.java.
Constraints: Write a @Query or derived method to fetch all work orders by assignee_id so an in-charge can retrieve their specific oversight queue, ordered by deadline ascending or priority.

Task 3: Work Order Service (Business Logic)
Create WorkOrderService.java.
Constraints: Write a @Transactional method assignInCharge(UUID complaintId, UUID inChargeId, String priority, LocalDateTime deadline). This method must:

Create or update the WorkOrder, setting the assignee_id, priority, and deadline.

Automatically update the parent Complaint status from PENDING to IN_PROGRESS (or ASSIGNED).

Log this state transition in the complaint_history table.

Task 4: Controller Endpoints
Create WorkOrderController.java. Expose REST endpoints:

POST /api/work-orders/{complaintId}/assign: Allows an in-charge to claim/assign a ticket.

PUT /api/work-orders/{id}: Allows the in-charge to update the deadline, priority, or add completion_notes after the offline worker finishes the job.

GET /api/work-orders/me: Fetches the currently authenticated in-charge's active oversight queue.
```

## Constraints

- Spring Boot
- Restrict who can be an assignee
- Verify the data model first

## Refinements

- **The data model could not support the prompt as written.** There was no in-charge role, a stored role would have been reset at every login, there was no notion of territory, and existing code contradicted the design.
- **We redefined the role:** a `STAFF` role for hostel staff who are not students, added by admins by email in the app, seeing all complaints and only able to assign themselves.
- **We decided the workflow:** taking a complaint makes it Assigned, starting work makes it In Progress, and marking it Done resolves it. Removing a staff member returns their complaints to Pending.
- **Staff management was extended** at our request with each person's open and overdue complaints, and reassigning work when someone is removed.
- **The work order follows direct status changes,** so a stale one cannot reopen a rejected complaint (`54e0fcf`).

## Outcome

The AI stated that the `STAFF` role needed no database change, which was wrong: the live `users` table had a check constraint allowing only the old roles, so adding staff failed. We found it while testing in the app, and fixed it with `schema_nov06.sql` and a Postgres regression test in `dff9af3`, in PR #32.
