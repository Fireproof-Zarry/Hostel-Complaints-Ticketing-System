# Admin Complaint Listing and Filters

| | |
|---|---|
| **Goal** | Provide an admin API to list complaints with optional filters. |
| **Date** | 2026-10-05 |
| **Pull requests** | #6 |

## Context

Administrators needed an API to retrieve complaints and narrow down the results using complaint attributes and date ranges.

## Prompt

```text
Implement the backend API for listing complaints for administrators.

Create the endpoint:

GET /api/admin/complaints

Support optional query parameters:

- status
- category
- from
- to

Implement the necessary repository methods and service logic to select complaints based on the supplied filters.

Ensure that filters can be used optionally.

Add service and controller tests to verify the listing and filtering behavior.