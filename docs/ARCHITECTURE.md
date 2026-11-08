# Architecture

## Overview

```mermaid
flowchart LR
    Browser["React app<br/>(browser or installed PWA)"]
    Google["Google sign-in"]
    API["Spring Boot API"]
    DB[("Supabase PostgreSQL")]
    Storage[("Supabase Storage<br/>complaint photos")]
    FCM["Firebase Cloud Messaging"]

    Browser -- "1. sign in" --> Google
    Browser -- "2. REST + Google token" --> API
    API --> DB
    API --> Storage
    API -- "push" --> FCM
    FCM -- "notification" --> Browser
```

There is one backend and one database. The backend holds no sessions: the browser sends its Google ID token with every request.

### Backend layout

Code is in `backend/src/main/java/com/example/demo/hostel/`.

| Package | Purpose |
|---|---|
| `controller` | HTTP endpoints |
| `service` | Business rules |
| `repository` | Database queries |
| `model` | Database entities |
| `dto` | What the API sends and receives |
| `config` | Security, rate limiting, Firebase |
| `specification` | Admin complaint filters |
| `validation` | Hostel room and floor rules |

## Roles and sign-in

| Role | Who | How the role is decided |
|---|---|---|
| `STUDENT` | Verified `@smail.iitm.ac.in` accounts, and only Bhadra residents once the resident list is uploaded | Created automatically on first sign-in |
| `STAFF` | Hostel staff, on any Google account | An admin adds their email in Staff management |
| `ADMIN` | A small fixed group | Listed in the `ADMIN_EMAILS` setting |

Admins are configured rather than stored, because they rarely change and someone has to be able to add the first staff member.

The login page has two tabs, both handled by `POST /api/auth/login`:

| | Student Login | Staff Login |
|---|---|---|
| New smail student | Account created | 403 |
| Smail student not on the resident list | 403, "This portal is for Bhadra residents" | 403 |
| Existing student | Signed in | 403 |
| Other personal email | 401 | 403 |
| Staff added by an admin | 401, "use Staff Login" | Signed in |
| Listed admin | Refused, "use Staff Login" | Signed in (created on first sign-in) |

An smail address only counts if Google also confirms the domain through the token's `hd` claim.

**Resident list.** Admins upload the hostel's roll numbers on the **Residents** page (`ResidentService`, table `residents`). A student's roll number is the part of their address before `@smail.iitm.ac.in`, for example `cs23b051`. While the list is empty, every smail student can sign in, so deploying the feature locks nobody out. Staff and admins are never checked against it. The list is checked on every student request, so it is kept in memory: reloaded after each change, and every 5 minutes in case the table is edited directly.

The tabs are only the entrance. **Every** later request is checked again by `CustomJwtAuthenticationConverter`, which calls `AuthService.processGoogleLogin`, so a removed staff member, or a student taken off the resident list, is blocked on their next action.

`SecurityConfig` applies these URL rules:

| Path | Allowed |
|---|---|
| `/health`, `POST /api/auth/login` | Anyone |
| `/api/admin/staff/**` | Admins |
| `/api/admin/**`, `/api/work-orders/**` | Admins and staff |
| Everything else | Any signed-in user |

## Complaint workflow

```mermaid
stateDiagram-v2
    [*] --> PENDING: filed
    PENDING --> ASSIGNED
    PENDING --> IN_PROGRESS
    PENDING --> REJECTED
    ASSIGNED --> IN_PROGRESS
    ASSIGNED --> RESOLVED
    ASSIGNED --> PENDING
    ASSIGNED --> REJECTED
    IN_PROGRESS --> RESOLVED
    IN_PROGRESS --> ASSIGNED
    IN_PROGRESS --> PENDING
    IN_PROGRESS --> REJECTED
    RESOLVED --> PENDING: reopened
    RESOLVED --> IN_PROGRESS: reopened
    REJECTED --> PENDING: reopened
    REJECTED --> IN_PROGRESS: reopened
```

`ComplaintTransitionService` is the only place a status changes. It refuses any other move with a 409, writes the history row in the same transaction, and notifies the reporter after the transaction commits.

- `ASSIGNED → RESOLVED` is allowed for small jobs nobody marked as started. `PENDING → RESOLVED` is not.
- The frontend's `STATUS_TRANSITIONS` in `AdminHome.jsx` must match these rules.

## Work orders

A work order means someone has taken responsibility for a complaint. A complaint has at most one active work order, enforced by a partial unique index.

| Work order status | Complaint becomes |
|---|---|
| Created (`OPEN`) | `ASSIGNED`, if it was `PENDING` |
| `IN_PROGRESS` | `IN_PROGRESS` |
| `DONE` | `RESOLVED`, with the notes in its history |
| `CANCELLED` | `PENDING` and unassigned |

The link works both ways. If someone changes a complaint's status directly, `WorkOrderService.syncWithComplaintStatus` updates the work order:

| Complaint set to | Active work order becomes |
|---|---|
| `RESOLVED` | `DONE` |
| `REJECTED` | `CANCELLED` |
| `PENDING` | `CANCELLED`, and the complaint is unassigned |
| `IN_PROGRESS` | `IN_PROGRESS`; a closed complaint resumes its last work order |
| `ASSIGNED` | `OPEN` |

Staff can only assign themselves, and only if nobody else holds the complaint. Removing a staff member moves their active work to someone else, or returns it to Pending.

Deadlines are hostel wall-clock times in IST, so `WorkOrderService` compares them with the time in IST. Other timestamps are set by the server and sent as UTC without an offset; the frontend reads them with `utils/serverDate.js`.

## Nudges

The person who filed an unresolved complaint can nudge the admins if nothing seems to be happening.

- **When it opens:** 5 AM IST on the 7th day after the complaint was filed, whatever time it was filed. A complaint filed any time on the 6th opens at 5 AM on the 13th.
- **Repeat nudges:** the next one opens the same way, counted from the day of the last nudge.
- **What it does:** sets `last_nudged_at` and sends a push notification to every admin with notifications on. A nudge doesn't change the complaint's status or add to its history.
- **On the admin dashboard:** unresolved nudged complaints sort to the top, latest nudge first, with a bell icon. Once a complaint is closed, it loses the badge and its place at the top.
- **Clicking the notification:** this opens `/admin?complaint={id}`, which clears any filters hiding the complaint and highlights it for 10 seconds, or until the next click.

`NudgeService` holds the rules. `ComplaintRepository.recordNudge` repeats the checks in one conditional `UPDATE`, so if two nudges arrive together only one succeeds and only one notification goes out. That update skips `@Version` and `@UpdateTimestamp`, so a nudge doesn't count as editing the complaint.

Times are stored in the server's time zone, so `NudgeService` converts them to IST to work out the day.

## Duplicate detection

While someone types a title, `GET /api/complaints/potential-duplicates` searches open complaints using PostgreSQL's `pg_trgm` similarity, with a threshold of 0.3, and returns up to three. It searches the same floor for community issues and the same room for room issues. Students can upvote a match instead of filing a new complaint.

## Limits

| Limit | Value | Where |
|---|---|---|
| Open complaints (Pending, Assigned, In Progress) | 5 for students, 50 for staff and admins | `ComplaintService` |
| Filing rate | 5 per minute per user | `ComplaintRateLimiter` |
| Photo size | 5 MB; JPEG, PNG or WebP | `SupabaseStorageService` |

The rate limit is held in memory, which suits a single server instance.

## Deleting and archiving

| Job | When | What it does |
|---|---|---|
| Delete | Owner, while Pending | Soft delete: sets `deleted_at`, which hides the complaint everywhere |
| `ComplaintArchivalService` | Daily, 2:00 AM IST | Archives complaints resolved or rejected over 30 days ago |
| `ComplaintPurgeService` | Daily, 3:00 AM IST | Permanently removes complaints deleted over 15 days ago, and their photos |

Archived complaints leave the admin dashboard, but their owners can still see them. Reopening a complaint brings it back.

## Analytics

`GET /api/admin/analytics` returns all the dashboard's figures at once. `AnalyticsRepository` runs native PostgreSQL queries, and `AnalyticsService` combines them.

- **Date range:** this applies to when complaints were filed. Resolution figures use when they were resolved.
- **Resolution rate:** resolved complaints as a percentage of all complaints except rejected ones.
- **Average resolution time:** hours from filing to the latest move to Resolved, for complaints that are currently Resolved.
- **Recurring issues:** the same category reported at least twice in one room, or in one floor and wing.
- **Soft-deleted complaints** are never counted. Archived ones are, so old complaints still count towards trends.

## Concurrency

`Complaint` has a `@Version` field. If two people change a complaint at the same moment, the second gets a 409 instead of overwriting the first.

## Notifications

Notifications use Firebase Cloud Messaging. The frontend asks for permission, gets a device token, and saves it with `PUT /api/users/{id}/fcm-token`.

| Event | Who is told |
|---|---|
| Complaint status changes | The person who filed it |
| A complaint is assigned to someone else | The new assignee |
| A complaint is nudged | Every admin with notifications on |

Assigning yourself and editing a priority or deadline send nothing.

What the person sees depends on the page:

| HostelFix tab | Result |
|---|---|
| Visible and focused | In-app pop-up |
| Visible but not focused | In-app pop-up and system notification |
| Hidden or closed | System notification, from `firebase-messaging-sw.js` |

Clicking a notification opens the complaint it is about: `/student?complaint={id}` for a status change, which switches to My Complaints, clears the filters and scrolls to it, and `/admin?complaint={id}` for a nudge. If HostelFix is already open, the service worker tells that page to navigate instead of opening a new tab.

Things to know:
- Nothing is stored, so there is no notification history. The timeline shows every status change.
- Each user has one saved device. Registering a new one replaces the old one.
- Logging out removes the device, so signed-out users get nothing.
- Notifications are sent only after the change is committed, and a failed send never fails the request.

## Security notes

- Google tokens are verified against Google's keys and this app's client ID.
- API responses for students leave out other people's account details; history shows staff as "Hostel office".
- Secrets stay out of Git: `.env` files and the Firebase service account file are ignored.
