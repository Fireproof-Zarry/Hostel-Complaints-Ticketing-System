# Prompts

The main AI prompts behind HostelFix, kept so that AI-assisted changes can be traced and reproduced.

Each file records:
- **Context:** what existed and why the change was needed
- **Prompt:** the prompt, word for word
- **Constraints:** the rules it set
- **Refinements:** the corrections that changed the result
- **Outcome:** what shipped and how it was checked

Refinements matter most. They show where the first answer was wrong or incomplete, and what we decided instead.

## Index

| # | Topic | Pull requests | Commits |
|---|---|---|---|
| [01](01-database-normalization.md) | Database normalization and atomic status changes | #15, #16 | `522632b`, `4a692f7` |
| [02](02-image-upload.md) | Complaint photo upload | #17 | `4dbfba2` |
| [03](03-fcm-notifications.md) | Push notifications for status changes | #19 | `8b29251` |
| [04](04-complaint-archiving.md) | Automatic archiving | #21 | `ec37f50` |
| [05](05-duplicate-detection.md) | Duplicate detection with pg_trgm | #25 | `84e132a`, `c742bd5` |
| [06](06-community-dashboard.md) | Community dashboard | #26 | `f761206`, `2d86540` |
| [07](07-production-hardening.md) | Rate limiting and tuning for a 512 MB server | #27 | `0082634` |
| [08](08-optimistic-locking.md) | Optimistic locking on complaints | #27 | `bd5dfdd` |
| [09](09-work-orders.md) | Work orders and hostel staff | #29 | `6af6d51`, `f5b5deb`, `08deaba`, `54e0fcf` |
| [10](10-status-workflow.md) | Status workflow and history API | #30 | `bb6f8c3` |
| [11](11-complaint-timeline.md) | Complaint timeline | #31 | `8311d3b`, `a5abed6` |
| [12](12-student-staff-login.md) | Student Login and Staff Login | #32 | `43683c8` |
| [13](13-admin-analytics.md) | Admin analytics dashboard | #28 | `653e3ac`, `ea6bce4` |
| [14](14-notification-complaint-link.md) | Link status notifications to complaints | #39 | `6e7c4ab` |
| [15](15-nudge-feature.md) | Nudge unresolved complaints | #36 | `bdb65be` |
| [16](16-notification-toast-and-settings.md) | Notification toasts, settings and logout cleanup | #35 | `a54a7d8` |
| [17](17-unified-complaint-management-modal.md) | Unified complaint management modal | #12 | `89e2263` |
| [18](18-admin-endpoint-authorization.md) | Admin endpoint authorization | #11 | `22fc51b` |
| [19](19-admin-complaint-assignment.md) | Admin complaint assignment | #10 | `7331d92` |
| [20](20-complaint-status-transitions.md) | Complaint status transition rules | #7 | `ccd84f1` |
| [21](21-admin-complaint-listing.md) | Admin complaint listing and filters | #6 | `c984be8` |
