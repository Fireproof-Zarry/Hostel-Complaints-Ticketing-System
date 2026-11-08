# Automatic archiving

| | |
|---|---|
| **Goal** | Soft-archive complaints that were resolved or rejected more than 30 days ago, so the admin dashboard stays fast. |
| **Date** | 2026-10-24 |
| **Pull requests** | #21 |
| **Commits** | `ec37f50` |

## Context

The admin dashboard loaded every complaint ever filed.

## Prompt

```text
Analyze these tasks first, of there are any issues or modifications required let me know beofre you start implementing. If there are no issues, you can start the implementation:

Context: We are implementing an automated Archiving System for the HostelFix Spring Boot backend. We want to soft-archive old RESOLVED or REJECTED complaints to optimize active dashboard query performance.

Task 1: Database Migration & Indexing
Create src/main/resources/schema_oct24.sql with the following:
Add the boolean flag: ALTER TABLE complaints ADD COLUMN is_archived BOOLEAN DEFAULT FALSE;
Add a partial index to speed up dashboard queries: CREATE INDEX idx_active_complaints ON complaints (status) WHERE is_archived = false;

Task 2: JPA Entity & Repository Updates
Update Complaint.java to include boolean isArchived.
Update ComplaintRepository.java. Modify the existing dashboard fetch methods (e.g., findAllBy...) to explicitly include findByIsArchivedFalse(...).
Add a specific method for the admin audit view: List<Complaint> findByIsArchivedTrue();

Task 3: Automated Archiving Job (Cron)
Create ComplaintArchivalService.java.
Constraints:
Annotate the class with @Service and the method with @Scheduled(cron = "0 0 2 * * ?") so it runs at 2:00 AM every day. (Ensure @EnableScheduling is on the main application class).
The method should query the database for complaints where status is RESOLVED or REJECTED AND the updated_at (or changed_at from the history) is older than 30 days.
Iterate through the results, set isArchived = true, and save them.
Wrap this execution in a @Transactional block.

Task 4: Admin API
Update ComplaintController.java to add GET /api/complaints/archived so hostel managers can still view the historical archive when necessary.
```

## Constraints

- A nightly scheduled job
- Soft archive: nothing is deleted
- Admins can still see archived complaints

## Refinements

The prompt asked for the tasks to be analysed first. The AI found five issues and several smaller points, and we accepted all of its recommendations:

- **The archive endpoint would have been public to students,** exposing other students' details. It moved to `GET /api/admin/complaints/archived`.
- **The repository changes would not have affected the dashboard,** which uses `ComplaintSpecification`. The archived filter was added there instead.
- **Archived complaints needed a way back.** Reopening a complaint now un-archives it.
- **The migration had to be safe to re-run** with a required column, using `ADD COLUMN IF NOT EXISTS ... NOT NULL DEFAULT FALSE`.
- **"2 AM" would have meant 7:30 AM India time** on a UTC server, so the job runs with `zone = "Asia/Kolkata"`.
- **One bulk `UPDATE` instead of a save loop:** one database round trip, and it keeps each complaint's real `updated_at`.

## Outcome

Students still see their own archived complaints; only the admin dashboard hides them. We asked for a written summary of exactly what was implemented before committing.
