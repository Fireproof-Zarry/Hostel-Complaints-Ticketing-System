
### 2. `prompts/16-notification-toast-and-settings.md`

```markdown
# Notification Toasts and Settings

| | |
|---|---|
| **Goal** | Improve the student notification experience with foreground toasts, notification preferences, and FCM token cleanup on logout. |
| **Date** | 2026-11-06 |
| **Pull requests** | [#35](https://github.com/Fireproof-Zarry/Hostel-Complaints-Ticketing-System/pull/35) |
| **Commits** | 1 commit |

## Context

The application already supported push notifications, but the student-facing experience needed better handling when notifications arrived while the application was open. Students also needed control over notifications, and browser FCM tokens needed to be cleaned up on logout.

## Prompt

> **Reconstructed summary — not verbatim.**
>
> Improve the frontend notification experience. Display toast notifications for incoming foreground messages across all student pages. Update complaint and Community data without requiring a page reload. Add a notification on/off switch on the Notifications page and remember the student's preference in the current browser. Remove the browser's registered FCM token when the student logs out. Also ensure the complaint list is displayed immediately when returning from My Profile.

## Constraints

- Foreground notification toasts should work across all student pages.
- Complaint and Community views should update without a manual page reload.
- Notification preferences should be remembered per student in the current browser.
- The browser's FCM token should be removed on logout.

## Refinements

- Keep the notification preference on the Notifications page.
- Restore the last complaint list immediately when navigating back from My Profile.

## Outcome

- Added foreground notification toast handling across student pages.
- Updated complaint and Community data without requiring a reload.
- Added a notification on/off switch with a per-student, per-browser preference.
- Added FCM token cleanup during logout.
- Improved navigation from My Profile by showing the last complaint list immediately.