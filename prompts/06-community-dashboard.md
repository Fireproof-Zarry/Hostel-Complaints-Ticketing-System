# Community dashboard

| | |
|---|---|
| **Goal** | A page where students can browse and upvote open issues in shared areas. |
| **Date** | 2026-11-01 |
| **Pull requests** | #26 |
| **Commits** | `f761206`, `2d86540` |

## Context

Two prompts: the backend query and endpoint, then the React page. Before the frontend, we asked for a privacy problem in the history endpoint to be fixed first.

## Prompt

### Prompt 1

```text
Context: We are building the "Community Dashboard" for the HostelFix ticketing system so students can view and upvote shared issues (like corridor lights or washrooms).

Task 1: Architecture Verification (DO THIS FIRST)
Review the plan to create a dedicated backend fetch query for community complaints. Check my existing ComplaintRepository.java and ComplaintController.java. Verify if we already have an endpoint that efficiently fetches active complaints where is_community_area = true, sorted by the number of upvotes or creation date. If an existing endpoint can handle this via query parameters (e.g., ?type=community&sort=upvotes), point it out so we don't write redundant code. If there are no issues or missing pieces, proceed to implementation.

Task 2: Repository Update
If a new query is needed, update ComplaintRepository.java.
Constraints: Write a @Query to fetch active (isArchived = false) complaints where is_community_area = true. Ensure the query includes a way to count the associated upvotes so we can sort the results by highest upvotes first.

Task 3: Controller API
Update ComplaintController.java to expose this query (e.g., GET /api/complaints/community). It must return the complaint details, the total upvote count, and a boolean indicating if the currently authenticated user has already upvoted it.
```

### Prompt 2

```text
Context: We are building the React (Vite) frontend for the HostelFix Community Dashboard using Tailwind CSS. The backend API to fetch community complaints and the POST /api/complaints/{id}/upvote toggle endpoint are ready.

Task 1: Architecture Verification (DO THIS FIRST)
Review the plan to build a CommunityDashboard.jsx view. Check my existing React routing and main dashboard layout. Verify the best place to integrate this (e.g., as a separate route /community or as a tab toggle on the main Dashboard.jsx). If you find any prop-drilling risks or state management conflicts with the existing personal complaints feed, let me know before writing code.

Task 2: Dashboard Component
Create or update the Community Dashboard UI.
Constraints:

Fetch data from the community complaints endpoint on mount.

Render the complaints as Tailwind-styled cards displaying the title, description, wing/location, and current status.

Implement the Upvote button. It must display the current upvote count and change styling (e.g., filled vs. outline icon) based on whether the logged-in student has already upvoted it.

Task 3: Optimistic UI Updates
When a user clicks the Upvote button, immediately toggle the visual state and increment/decrement the count in the local React state before the backend API call resolves. If the API call fails, silently revert the state to prevent UI desync.
```

## Constraints

- Check for an existing endpoint before writing a new one
- Sorted by upvotes or date
- Include the upvote count and whether you upvoted

## Refinements

- **No existing endpoint could be reused,** so a new `GET /api/complaints/community` was added, confirmed against every existing complaint query.
- **One query per page,** counting upvotes and "you upvoted this" in the same query.
- **Never includes the reporter's details,** and room complaints never appear.
- **Optimistic upvotes** on the page, rolled back if the request fails.

## Outcome

All backend tests that could run locally passed; the Postgres-only tests ran in CI.
