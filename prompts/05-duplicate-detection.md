# Duplicate detection with pg_trgm

| | |
|---|---|
| **Goal** | Warn students while they type if a similar open complaint already exists, so they can upvote it instead of filing a duplicate. |
| **Date** | 2026-10-31 |
| **Pull requests** | #25 |
| **Commits** | `84e132a`, `c742bd5` |

## Context

Students were filing the same community issue many times. The prompt was sent once without its Purpose paragraph, interrupted, and resent with it; the resent version is below.

## Prompt

```text
Context: We are implementing Duplicate Detection for the HostelFix ticketing system. We will use PostgreSQL's native pg_trgm (Trigram) extension for fast, local text similarity matching.

Purpose: Whenever students are submitting community issues (restroom, water dispenser, etc), multiple students could create the same complaints and this becomes a hassle for the admin to update and resolve. This would not happen to student specific issues as student issues are about their own rooms and there is no sharing room in Bhadra hostel (all are single-occupancy). When a student is creating a community issue, we check for similarities between existing complaints in the same floor (and/or same wing) and show it on the screen to the student so that they can stop creating that complaint if it already exists.

Directive: Go throught the tasks and if you think they have issues, tell the issue and change the task so that it is correct. Do not proceed with implementing the changed task, ask for confirmation first. If you are not changing a task, then proceed with the implementation.

Task 1: Database Migration (pg_trgm)
Create src/main/resources/schema_oct25.sql.
Constraints:
Add the command to enable the extension: CREATE EXTENSION IF NOT EXISTS pg_trgm;
Add a GiST or GIN index on the complaints table for the title and description columns to optimize trigram searches.

Task 2: Spring Data JPA Repository
Update ComplaintRepository.java.
Constraints: Write a custom @Query that uses the <-> operator or the similarity() function to find complaints where the title or description is similar to the user's input. Filter only by complaints that are currently PENDING or IN_PROGRESS (ignore resolved/archived tickets). Order by similarity descending, limit to top 3.

Task 3: Backend API
Create DuplicateDetectionService.java and expose a new endpoint in ComplaintController.java: GET /api/complaints/potential-duplicates.
Constraints: It should accept a title string and optionally a location_id (so we only show duplicates on the same floor/wing). Call the repository and return the matches.

Task 4: React UI Modal
Update the React complaint submission form (ComplaintForm.jsx).
Constraints:

Add a debounce hook (e.g., 500ms) to the title input.

When the user types a title, trigger the potential-duplicates API.

If duplicates are found, display a Tailwind-styled warning Modal or inline Banner: "Wait! Has this already been reported?" showing the duplicate ticket titles and their current status.

Give the user two buttons: "Yes, this is my issue" (which lets them upvote the existing ticket instead of creating a new one) or "No, mine is different" (which allows them to proceed with submission).
```

## Constraints

- PostgreSQL's `pg_trgm` extension
- A migration safe to re-run
- The check must never block filing

## Refinements

The AI implemented the migration as written but raised problems with the other three tasks before building them. We approved the corrected versions:

- **The query missed `ASSIGNED` complaints,** and would have compared private room complaints too. It now covers all open statuses, community areas only, on the same floor.
- **A minimum similarity of 0.3** stops it always returning three results, and `word_similarity` is used for long descriptions.
- **Returning full complaints would leak student details,** so it returns a small summary instead.
- **The upvote endpoint toggles,** so pressing "Yes, this is my issue" on an already-upvoted complaint would have removed the upvote. The response now says whether you have upvoted.
- **A banner instead of a modal,** so typing is not interrupted, with stale responses ignored.
- **Extended to room complaints** at our request, for hostels with shared rooms (`c742bd5`).

## Outcome

The 0.3 threshold was kept after checking it against real complaint titles in our database: "Fan not working" scored 0.83 and matched, while "Sink tap broken" scored 0.25 and correctly did not.
