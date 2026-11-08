# Push notifications for status changes

| | |
|---|---|
| **Goal** | Notify students through Firebase Cloud Messaging when their complaint's status changes. |
| **Date** | 2026-10-23 |
| **Pull requests** | #19 |
| **Commits** | `8b29251` |

## Context

Students had to check the app to see progress. This prompt covered the backend only.

## Prompt

```text
Context: We are integrating Firebase Cloud Messaging (FCM) into our HostelFix Spring Boot backend to send push notifications to students when their complaint status changes.

Task 1: Dependencies & Database

Read my pom.xml and add the firebase-admin dependency.

Create src/main/resources/schema_oct22.sql with an ALTER TABLE users ADD COLUMN fcm_token VARCHAR; statement.

Update the User.java JPA entity to include the String fcmToken field.

Task 2: Firebase Configuration
Create FirebaseConfig.java in a config package.
Constraints: Initialize FirebaseApp using GoogleCredentials.fromStream(). Read the path to the service account JSON file from application.properties using @Value("${firebase.config.path}").

Task 3: FCM Service (Asynchronous)
Create FcmNotificationService.java.
Constraints:

Write a method public void sendStatusUpdateNotification(String targetToken, String complaintTitle, String newStatus).

Annotate this method with @Async so the HTTP call to Google's servers does not block our main database threads.

Build the Message using the Firebase Admin SDK and handle FirebaseMessagingException safely (log the error, do not crash the app).

Task 4: Device Token API
Update (or create) UserController.java with a PUT /api/users/{id}/fcm-token endpoint.
Constraints: Accept a simple JSON payload or query param with the token, update the user's fcmToken in the database, and save it.

Task 5: Wire it to the Business Logic
Read my existing ComplaintTransitionService.java.
Constraints: Inside changeComplaintStatus, right after saving the new ComplaintHistory, check if the complaint's creator has an fcmToken. If they do, call fcmNotificationService.sendStatusUpdateNotification(...) to alert them of the change
```

## Constraints

- Firebase Admin SDK
- A failed notification must never fail the request
- Each user stores one device token

## Refinements

- **Firebase is optional.** If no service account path is set, Firebase is skipped and the app still starts, which lets the tests run without credentials.
- **`@Async` needs `@EnableAsync`.** It was added, because without it the "background" sends would block the request.
- **Sent only after the change is saved.** The notification is queued until the transaction commits, so a rolled-back change never reaches a phone.
- **Dead tokens are cleared** when Firebase reports them as unregistered.
- **Merge order.** We asked what would happen if the admin status PR merged first; notifications from the admin dashboard depended on it.

## Outcome

All 100 backend tests passed, 22 of them new. No real notification could be sent at the time, because no Firebase key was available locally; real notifications were confirmed later, once the frontend registered devices.
