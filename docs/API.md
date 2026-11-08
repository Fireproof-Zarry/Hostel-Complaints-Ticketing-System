# API reference

All paths are on the backend, for example `http://localhost:8080/api/auth/me`.

Unless marked **Public**, send the Google ID token on every request:

```
Authorization: Bearer <token>
```

## Common errors

| Status | Meaning |
|---|---|
| 400 | Invalid input |
| 401 | Missing or rejected sign-in |
| 403 | Your role cannot do this |
| 404 | Not found. Also returned for other students' complaints, so they aren't revealed |
| 409 | Not allowed in the current state, or someone changed it at the same moment |
| 429 | Limit reached. With a `Retry-After` header it's the filing rate; without it, the open-complaint limit |

Admin, staff, work-order and login errors return a reason the frontend can show:

```json
{ "error": "Forbidden", "message": "Staff can only assign complaints to themselves" }
```

## Health

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/health` | **Public** | Checks the backend is running |

## Authentication

### `POST /api/auth/login` (Public)

Called by the login page.

```json
{ "credential": "<Google ID token>", "loginType": "STUDENT" }
```

`loginType` is `STUDENT` or `ADMIN` (the Staff Login tab). Returns the user. See [the rules](ARCHITECTURE.md#roles-and-sign-in) for which accounts get a 401 or 403.

### `GET /api/auth/me`

Returns the signed-in user: `id`, `email`, `name`, `role`, `fullName`, `phoneNumber`, `rollNumber`, `roomNumber`.

## Users

A user can only change their own account.

| Method | Path | Description |
|---|---|---|
| PUT | `/api/users/{id}/profile` | Updates `fullName` and `phoneNumber`, plus `rollNumber` and `roomNumber` for students |
| PUT | `/api/users/{id}/fcm-token` | Saves the notification device: `{ "token": "..." }` |
| DELETE | `/api/users/{id}/fcm-token` | Removes the notification device |

## Complaints

| Method | Path | Description |
|---|---|---|
| POST | `/api/complaints` | Files a complaint |
| GET | `/api/complaints/mine` | The caller's complaints, newest first, with `assignee` (`name`, `phoneNumber`) while a work order is active, plus `canNudge` and `nextNudgeAt` |
| GET | `/api/complaints/{id}` | One of the caller's complaints |
| GET | `/api/complaints/{id}/history` | Status changes, newest first. Owner, or any admin or staff member |
| DELETE | `/api/complaints/{id}` | Soft-deletes the caller's complaint. Pending only, otherwise 409 |
| POST | `/api/complaints/{id}/image` | Attaches a photo: multipart field `file` |
| GET | `/api/complaints/community` | Open community complaints |
| GET | `/api/complaints/potential-duplicates` | Similar open complaints |
| POST | `/api/complaints/{id}/upvote` | Adds or removes the caller's upvote, for students only |
| POST | `/api/complaints/{id}/nudge` | Nudges the admins about the caller's own unresolved complaint |

### `POST /api/complaints`

```json
{
  "title": "Fan not working",
  "description": "Stopped last night",
  "category": "Electrical",
  "isCommunityArea": false,
  "floor": "First",
  "wing": "East wing",
  "room": "204",
  "availableTimeSlot": "Mon: 2 PM - 5 PM",
  "keyWithSecurity": false
}
```

| Field | Rule |
|---|---|
| `title`, `description`, `category` | Required. Categories: Electrical, Plumbing, Carpentry, Cleaning, IT |
| `floor`, `wing` | Required when `isCommunityArea` is true |
| `room` | Staff and admins only, from 101 to 799. Students always use their profile room |
| `availableTimeSlot` | Optional |
| `keyWithSecurity` | Room complaints only |

Returns 422 if a room complaint has no valid room.

### `GET /api/complaints/community`

| Parameter | Default | Description |
|---|---|---|
| `floor` | All | Filters by floor |
| `sort` | `upvotes` | `upvotes` or `recent` |
| `page`, `size` | `0`, `20` | Paging |

### `GET /api/complaints/potential-duplicates`

| Parameter | Description |
|---|---|
| `title` | Required; shorter than 4 characters returns nothing |
| `communityArea` | `true` by default |
| `floor`, `wing` | For community issues; `floor` is required |
| `room` | For staff and admins searching a room. Ignored for students |

### `POST /api/complaints/{id}/nudge`

Returns when this nudge happened and when the next one is allowed:

```json
{ "complaintId": 21, "lastNudgedAt": "2026-11-13T00:30:00", "nextNudgeAt": "2026-11-19T23:30:00" }
```

Returns 409 if the complaint is closed, or if the 7-day wait hasn't passed. Returns 404 for anyone else's complaint. See [the rules](ARCHITECTURE.md#nudges).

In `/api/complaints/mine`, `canNudge` says whether it's allowed now. `nextNudgeAt` says when it opens, and is null once the complaint is closed. These timestamps are in the server's time zone, with no offset.

### History entry

```json
{
  "oldStatus": "PENDING",
  "newStatus": "ASSIGNED",
  "reason": "Assigned to Ravi Kumar",
  "changedAt": "2026-11-05T10:00:00",
  "changedBy": "Hostel office"
}
```

Students see "Hostel office" for staff and admins. Admins and staff see real names. Timestamps are UTC with no offset.

## Admin: complaints (admins and staff)

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/complaints` | Non-archived complaints, with unresolved nudged ones first. Each includes `lastNudgedAt` |
| GET | `/api/admin/complaints/{id}` | One complaint |
| GET | `/api/admin/complaints/archived` | Archived complaints |
| GET | `/api/admin/complaints/admins` | Everyone who can be assigned: admins and staff |
| PATCH | `/api/admin/complaints/{id}/status` | Changes status: `?status=RESOLVED&reason=...`. See [the workflow](ARCHITECTURE.md#complaint-workflow) |
| PATCH | `/api/admin/complaints/{id}/assign` | Assigns: `?email=...&priority=HIGH&deadline=2026-11-10T18:00:00`. Priority defaults to `MEDIUM`; the deadline is optional |

Filters for `GET /api/admin/complaints`, all optional:

| Parameter | Description |
|---|---|
| `status`, `category`, `floor`, `assignedTo` | Exact match |
| `from`, `to` | Filed between these date-times |
| `communityArea` | `true` for community issues, `false` for room issues |

## Work orders (admins and staff)

| Method | Path | Description |
|---|---|---|
| POST | `/api/work-orders/{complaintId}/assign` | Assigns a complaint |
| PUT | `/api/work-orders/{id}` | Updates a work order. Assignee or an admin |
| GET | `/api/work-orders/me` | The caller's active work, earliest deadline first |
| GET | `/api/work-orders/complaint/{complaintId}` | A complaint's work orders, newest first |

Assign:

```json
{ "inChargeId": "<user id>", "priority": "HIGH", "deadline": "2026-11-10T18:00:00" }
```

Leave out `inChargeId` to assign yourself. Priorities are `LOW`, `MEDIUM`, `HIGH` and `URGENT`. Staff can only assign themselves.

Update, with every field optional:

```json
{ "priority": "URGENT", "deadline": "2026-11-11T18:00:00", "completionNotes": "Replaced the capacitor", "status": "DONE" }
```

Status can be `IN_PROGRESS`, `DONE` or `CANCELLED`.

## Staff management (admins)

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/staff` | Staff and admins, with active and overdue counts |
| GET | `/api/admin/staff/{userId}/work-orders` | One person's active work orders |
| POST | `/api/admin/staff` | Adds staff: `{ "email": "..." }`. Returns 201 |
| DELETE | `/api/admin/staff/{userId}` | Removes staff. Add `?reassignTo={userId}` to hand over their work; otherwise it returns to Pending |

## Analytics (admins and staff)

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/analytics` | Everything the analytics dashboard shows |

`startDate` and `endDate` are optional dates such as `2026-11-01`, and both are inclusive. A start after the end returns 400.

| Response field | Contents |
|---|---|
| `overview` | `totalComplaints`, a count per status, `unresolved`, `resolutionRate`, `averageResolutionTimeHours` |
| `categoryBreakdown` | `category` and `count`, including categories with none |
| `floorBreakdown` | `floor` and `count` |
| `recurringIssues` | `type`, `floor`, `roomNumber`, `wing`, `category`, `count`, for the same category reported at least twice in one place |
| `monthlyTrends` | `month` (`yyyy-MM`), `created`, `resolved`. Months with no activity are included as zero. With no dates, it covers the last 12 months |

## Original student API

The app uses `/api/complaints`. These older endpoints still exist:

| Method | Path | Description |
|---|---|---|
| POST | `/api/student/complaints` | Files a complaint with `?title=&description=` |
| GET | `/api/student/complaints` | The caller's complaints |
