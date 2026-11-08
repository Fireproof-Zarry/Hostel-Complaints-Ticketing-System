# Complaint timeline

| | |
|---|---|
| **Goal** | Show students and admins a visual timeline of a complaint's status changes. |
| **Date** | 2026-11-05 |
| **Pull requests** | #31 |
| **Commits** | `8311d3b`, `a5abed6` |

## Context

The history API existed, but nothing displayed it. The prompt asked for the data flow to be checked first.

## Prompt

```text
Context: We are building the React (Vite) UI for the HostelFix ticketing system to display a visual audit timeline of updates and Work Order details. This needs to adapt based on whether a Student or Admin is viewing it.

Task 1: Component Verification (DO THIS FIRST)
Review my existing component structure for viewing a specific complaint (e.g., ComplaintDetail.jsx or the modal inside "My Complaints"). Verify how the current state and props are passed. Check if we are already fetching the WorkOrder data alongside the Complaint data. If the data fetching strategy will cause excessive re-renders or missing props, warn me and propose a fix before building the UI.

Task 2: Timeline Implementation
Build a vertical chronological timeline component using Tailwind CSS (e.g., a vertical line with circles for each status change).
Constraints:

Map over the data from GET /api/complaints/{id}/history.

Display the status change, timestamp (formatted to local time), and any transition notes.

Task 3: Role-Based View Logic
Extend the specific complaint view:

Student View: When accessed via "My Complaints", show the vertical timeline and basic scheduled time, but hide internal admin metrics (like priority or worker names, unless specified for public view).

Admin/In-Charge View: When accessed via the admin dashboard, display the timeline AND render a "Work Order Details" card alongside it. This card should show the assignee_id (the floor in-charge), the deadline, priority, and any completion_notes.
```

## Constraints

- A vertical timeline using Tailwind
- Students see the timeline but not internal details such as priority

## Refinements

- **History loads only when opened,** to avoid one request per complaint on page load.
- **A timezone bug was avoided.** Server times arrive as UTC without an offset, so a plain conversion would be 5.5 hours off. A small tested helper reads them correctly.
- **The spec asked for `assignee_id`,** which means nothing to a reader, so the card shows the assignee's name and email.
- **We redesigned the student view:** instead of a timeline button on each card, a **View** popup shows the photo, details, assigned staff and timeline (`a5abed6`).

## Outcome

Frontend tests covered the date handling. We reviewed the timeline in the running app, which led to the popup redesign.
