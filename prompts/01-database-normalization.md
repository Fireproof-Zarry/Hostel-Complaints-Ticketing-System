# Database normalization and atomic status changes

| | |
|---|---|
| **Goal** | Normalise the complaints table into categories and locations, add the work-order, comment and upvote tables, and make status changes atomic with an audit trail. |
| **Date** | 2026-10-19 |
| **Pull requests** | #15, #16 |
| **Commits** | `522632b`, `4a692f7` |

## Context

Complaints stored category, floor and room as plain text. There was no history of status changes. This work was split across three prompts, in order.

## Prompt

### Prompt 1

```text
Context: We are refactoring our HostelFix ticketing system from a basic setup to a normalized relational model. We are using PostgreSQL and Spring Data JPA.

Task 1: Context Gathering
Read my existing Complaint.java and User.java files in the src/main/java/**/model/ directory to understand the current package structure and existing UUID implementation.

Task 2: PostgreSQL DDL Migration
Create a schema.sql file in src/main/resources/ with the raw SQL to create the following reference and audit tables:

categories: category_id (UUID, PK), name (VARCHAR, Unique), default_sla_hours (INTEGER).

locations: location_id (UUID, PK), floor (VARCHAR), room_number (VARCHAR, Nullable), wing (VARCHAR, Nullable), is_community_area (BOOLEAN).

complaint_history: history_id (UUID, PK), complaint_id (UUID, FK to complaints), actor_id (UUID, FK to users), old_status (VARCHAR), new_status (VARCHAR), reason (TEXT, Nullable), changed_at (TIMESTAMP).

Add the SQL to schema.sql to ALTER the existing complaints table: drop the old raw string columns for category and location, and add category_id and location_id as strict Foreign Keys.

Task 3: Spring Boot JPA Entities

Create Category.java, Location.java, and ComplaintHistory.java in the model directory. Do not use Lombok; generate standard getters and setters.

Update the existing Complaint.java to replace the old string fields with @ManyToOne and @JoinColumn relationships mapping to Category and Location.

Update ComplaintHistory.java to use @ManyToOne for mapping complaint_id to Complaint and actor_id to User.
```

### Prompt 2

```text
Context: We are continuing our HostelFix database normalization. The users and complaints tables exist, and we just added categories, locations, and complaint_history.

Task 1: Context Gathering
Read Complaint.java and User.java in the src/main/java/**/model/ directory to understand the entity relationships.

Task 2: PostgreSQL DDL Migration
Create a new file named schema_oct18.sql in src/main/resources/ with the raw SQL to create the following tables:

work_orders: work_order_id (UUID, PK), complaint_id (UUID, FK to complaints, NOT NULL, ON DELETE CASCADE), assignee_id (UUID, FK to users, NOT NULL), priority (VARCHAR), status (VARCHAR), deadline (TIMESTAMP), completion_notes (TEXT), created_at (TIMESTAMP).

comments: comment_id (UUID, PK), complaint_id (UUID, FK to complaints, ON DELETE CASCADE), author_id (UUID, FK to users), body (TEXT, NOT NULL), created_at (TIMESTAMP).

upvotes: Composite Primary Key of (complaint_id, user_id). Both are FKs. Add created_at (TIMESTAMP).

Task 3: Spring Boot JPA Entities
Create WorkOrder.java, Comment.java, and Upvote.java in the model directory.
Constraints: Do not use Lombok; generate standard getters and setters. Use @ManyToOne and @JoinColumn for relationships. For Upvote, clearly implement the composite key using @EmbeddedId or @IdClass (e.g., creating an UpvoteId.java class if needed).

Task 4: Transactional Boundary Implementation
Create or update ComplaintTransitionService.java in the service package. Write a method called assignWorkOrder(UUID complaintId, UUID assigneeId, String priority, LocalDateTime deadline).
Constraints: Annotate this method with @Transactional so that updating the complaint status to 'IN_PROGRESS' and saving the new WorkOrder succeed or fail atomically.
```

### Prompt 3

```text
Context: Our PostgreSQL database has been updated with the full 8-table schema (users, complaints, categories, locations, complaint_history, work_orders, comments, upvotes).

Task 1: The Transactional Service
Create a new service class called ComplaintTransitionService.java. Implement a method:
public void changeComplaintStatus(UUID complaintId, UUID actorId, String newStatus, String reason)

Constraints:

This method MUST be annotated with Spring's @Transactional.

It must fetch the existing Complaint, record its current status, update the complaint to the newStatus, and save it.

It must instantiate a new ComplaintHistory entity linking the complaintId, actorId, oldStatus, newStatus, and reason, and save it via a ComplaintHistoryRepository.

Task 2: The Integration Test
Create a Spring Boot integration test file named ComplaintTransitionServiceIntegrationTest.java.

Constraints:

Write a test method that verifies the "Happy Path": changing a status successfully saves both the updated Complaint and the new ComplaintHistory record.

Write a test method that proves Atomicity (Rollback): intentionally throw a RuntimeException after the complaint status is updated but before the transaction commits, and assert that the Complaint status in the database rolled back to its original state. Use standard JUnit 5 and AssertJ.
```

## Constraints

- PostgreSQL and Spring Data JPA
- Keep the existing data: migrate it rather than starting again
- Migrations are run by hand, so they must be safe to re-run
- A status change and its history row must commit together or not at all

## Refinements

- **The IDs could not all be UUIDs.** The spec used UUIDs everywhere, but `complaints.id` is a `BIGINT` and `users.id` a `VARCHAR`. The foreign keys were matched to those types, and only the new tables got UUID primary keys.
- **The migration copies data before dropping columns.** Old category names are matched ignoring case and spaces, unknown ones get their own row, and each floor and room becomes a location.
- **No second service class.** `ComplaintTransitionService` already existed, so `changeComplaintStatus` was added to it instead of creating a duplicate.
- **Two PRs.** We split the work into the schema and entities (#15) and the transactional service (#16).

## Outcome

The migrations were tested on a throwaway Postgres 15 container loaded with messy legacy data, then run a second time to prove they are safe to re-run. An integration test forces the history write to fail and checks that the status change rolls back with it.
