# Admin Endpoint Authorization

| | |
|---|---|
| **Goal** | Restrict administrative APIs to users with the ADMIN role. |
| **Date** | 2026-10-06 |
| **Pull requests** | #11 |
| **Commits** | `22fc51b` (merge) |

## Context

The application had administrative endpoints that needed role-based access control. Authentication alone was insufficient because student accounts should not be allowed to access administrative functionality.

## Prompt

```text
Implement role-based authorization for administrative endpoints.

Restrict all /api/admin/** endpoints to users with the ADMIN role.

Use the authenticated user's email to retrieve their role from the database and ensure that the role is correctly mapped to Spring Security authorities.

Students must receive HTTP 403 when accessing admin endpoints, while unauthenticated requests must receive HTTP 401.

Add appropriate tests for role conversion and endpoint authorization.