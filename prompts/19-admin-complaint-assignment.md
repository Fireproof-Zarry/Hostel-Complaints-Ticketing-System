# Admin Complaint Assignment

| | |
|---|---|
| **Goal** | Allow administrators to assign complaints to valid admin users. |
| **Date** | 2026-10-06 |
| **Pull requests** | #10 |
| **Commits** | `7331d92` (merge) |

## Context

Administrators needed a way to assign complaints to individual admin users so that responsibility for handling complaints could be tracked.

## Prompt

```text
Implement the backend functionality for assigning complaints to administrators.

Create an endpoint:
PATCH /api/admin/complaints/{id}/assign

The endpoint should identify the requesting administrator using their email, validate that the user exists and has the ADMIN role, and assign the complaint to the specified administrator.

Store the assigned administrator's email against the complaint.

Handle invalid assignments and missing users appropriately.

Add service and controller tests for successful and unsuccessful assignment scenarios.