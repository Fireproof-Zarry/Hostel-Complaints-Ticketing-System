# Nudge for Unresolved Complaints

| | |
|---|---|
| **Goal** | Allow students to remind administrators about unresolved complaints at controlled intervals. |
| **Date** | 2026-11-07 |
| **Pull requests** | [#36](https://github.com/Fireproof-Zarry/Hostel-Complaints-Ticketing-System/pull/36) |
| **Commits** | 5 commits |

## Context

Students needed a way to follow up on complaints that remained unresolved for an extended period. The nudge feature provides a controlled reminder mechanism without allowing repeated notifications at arbitrary intervals.

## Prompt

> **Reconstructed summary — not verbatim.**
>
> Implement a nudge feature for unresolved complaints. Allow a student to nudge an eligible complaint starting at 5 AM IST, seven days after the day it was filed, and then once every seven days. Add an API to validate and process nudges, notify administrators through push notifications, and bring nudged complaints to the top of the admin list. Add a Nudge button to student complaint cards and visual indicators for nudged complaints in the admin interface.

## Constraints

- A complaint becomes eligible at 5 AM IST, seven days after its filing date.
- A student can nudge a complaint only once every seven days.
- Closed complaints cannot be nudged.
- Students can nudge only their own complaints.
- Return `409 Conflict` when a nudge is too early, has already been used for the current interval, or the complaint is closed.
- Return `404 Not Found` when the complaint does not belong to the requesting student.
- Simultaneous nudges must result in only one push notification.

## Refinements

- Show an inactive Nudge button in grey.
- On hover, explain when the button will become active using the message `Will be active X days later`.
- Provide administrators with a bell control to enable or disable nudge push notifications.
- Mark nudged complaints with a bell icon and a `Nudged on` indicator in the complaint details.
- Clicking a nudge push notification or toast should visually outline the corresponding complaint row.

## Outcome

- Added `POST /api/complaints/{id}/nudge`.
- Implemented eligibility, ownership, complaint-status, and repeat-nudge checks.
- Added push notifications for administrators and ensured simultaneous nudges trigger only one push.
- Added the student-side Nudge button and administrator-side visual indicators.
- Added a database migration requirement:

  ```sql
  ALTER TABLE complaints
  ADD COLUMN IF NOT EXISTS last_nudged_at TIMESTAMP(6);