# Optimistic locking on complaints

| | |
|---|---|
| **Goal** | Stop two people silently overwriting each other's changes to the same complaint. |
| **Date** | 2026-11-01 |
| **Pull requests** | #27 |
| **Commits** | `bd5dfdd` |

## Context

Two admins, or an admin and a scheduled job, could update a complaint at the same moment.

## Prompt

```text
Context: We need to guarantee transactional safety when multiple admins or workers try to update the same complaint simultaneously in our PostgreSQL database.

Task 1: Entity Update
Update Complaint.java by adding an @Version private Long version; field to enable Spring Data JPA Optimistic Locking.

Task 2: Concurrency Integration Test
Create ComplaintConcurrencyIntegrationTest.java.
Constraints: Write a test that fetches the same Complaint instance twice in two separate simulated threads/transactions. Modify both instances and attempt to save them sequentially. Assert that the second save throws an ObjectOptimisticLockingFailureException.
```

## Constraints

- JPA `@Version`
- A test proving the conflict is detected

## Refinements

The AI added four things the prompt did not mention, so that locking would work end to end:

- **A migration setting `version = 0` for existing rows,** because versioned updates misbehave on `NULL`.
- **The bulk archive update also bumps `version`,** because it bypasses the entity layer.
- **A conflict returns 409 with a readable message,** instead of a server error.
- **A test with real concurrent threads,** besides the two-transaction test the prompt asked for.

## Outcome

The concurrent test confirms one of two simultaneous updates gets the conflict.
