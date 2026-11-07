# HostelFix frontend

The React app for students, hostel staff and admins. For setup, see the [main README](../README.md).

## Commands

Use Node 22 or later. From this folder:

| Command | What it does |
|---|---|
| `npm install` | Installs dependencies |
| `npm run dev` | Starts the dev server on `http://localhost:5173` |
| `npm run build` | Builds for production; fails if Firebase settings are missing |
| `npm test` | Runs the unit tests |

Copy `.env.example` to `.env` first. Restart the dev server after changing it.

## Layout

| Path | Contents |
|---|---|
| `src/App.jsx` | Routes, role checks and session handling |
| `src/auth/session.js` | The sign-in token and `apiFetch` |
| `src/pages/Login.jsx` | Student Login and Staff Login |
| `src/pages/StudentHome/` | The student dashboard |
| `src/pages/Profile.jsx` | The student profile page |
| `src/pages/AdminHome.jsx` | The dashboard for admins and staff |
| `src/pages/AdminAnalytics/` | The analytics dashboard |
| `src/components/` | Shared and page-level components |
| `src/notifications/` | Push notification setup and listeners |
| `src/utils/` | Small helpers, with their tests alongside |

## Conventions

**Call the API with `apiFetch`.** Use it from `auth/session.js` instead of `fetch`. It adds the sign-in token and ends the session cleanly when the token is expired or rejected. The login page is the exception, because nobody is signed in yet.

**Tailwind's reset isn't loaded.** Only Tailwind's theme and utilities are used, so:
- Elements keep browser defaults. Add `m-0` to headings and paragraphs, and `list-none p-0` to lists.
- Buttons and inputs don't inherit the page font. The admin area fixes this in `AdminHome.css`.

**Dates:** server timestamps are UTC with no offset, so read them with `utils/serverDate.js`. Work order deadlines are IST wall-clock times, so don't pass them through it.

**Status rules:** `STATUS_TRANSITIONS` in `AdminHome.jsx` must match `ComplaintTransitionService` on the backend.

**Firebase's message listener:** Firebase keeps only one, so add listeners through `listenForForegroundMessages`, not `onMessage` directly. See `notifications/foregroundListeners.js`.

## Tests

Tests use Node's built-in runner and sit next to the code as `*.test.js`. They cover plain JavaScript helpers, not components.
