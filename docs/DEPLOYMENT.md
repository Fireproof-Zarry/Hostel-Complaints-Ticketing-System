# Deployment

HostelFix runs on three services:

| Service | Used for |
|---|---|
| **Supabase** | PostgreSQL database and complaint photos |
| **Firebase** | Push notifications |
| **Render** | The backend (Docker, 512 MB) and the frontend (static site) |

Sign-in uses a Google OAuth client from Google Cloud.

## Environment variables

### Backend (`backend/.env`, or Render's Environment page)

| Name | Required | Description |
|---|---|---|
| `DB_PASSWORD` | Yes | Supabase database password |
| `ADMIN_EMAILS` | Yes | Comma-separated admin emails |
| `SUPABASE_URL` | For photos | Your Supabase project URL |
| `SUPABASE_KEY` | For photos | Supabase key used to upload and delete photos. Keep it private |
| `FIREBASE_CONFIG_PATH` | For notifications | Path to the Firebase service account file |
| `SHOW_SQL` | No | Logs every SQL statement. Defaults to `true`; set `false` in production |

### Frontend (`frontend/.env`, or Render's Environment page)

| Name | Description |
|---|---|
| `VITE_API_URL` | The backend address, ending in `/api` |
| `VITE_GOOGLE_CLIENT_ID` | The Google OAuth client ID |
| `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_STORAGE_BUCKET`, `VITE_FIREBASE_MESSAGING_SENDER_ID`, `VITE_FIREBASE_APP_ID` | The Firebase web app settings. All six are required, or the app won't load or build |
| `VITE_FIREBASE_VAPID_KEY` | The Web Push key, needed for notifications |

Vite builds these values into the site, so **redeploy the frontend after changing them**. They're public by nature, but the backend's values are secret.

## Supabase

1. Run the migrations in order. See [Database](DATABASE.md#migrations).
2. Create a Storage bucket named `complaint-images`, with public read access.
3. Put the database password and storage settings in the backend environment.

The free tier has limited storage. Photos are the main use of it, because they stay after a complaint is resolved.

## Firebase

1. Create a project, then add a **Web app** to it. Its settings are the `VITE_FIREBASE_*` values.
2. Under **Project settings → Cloud Messaging → Web Push certificates**, generate a key pair. That's `VITE_FIREBASE_VAPID_KEY`.
3. Under **Project settings → Service accounts**, click **Generate new private key**.

Use one Firebase project for all of these.

**Keep the service account file secret.** Locally, save it as `backend/firebase-service-account.json`, which Git ignores, and set:

```
FIREBASE_CONFIG_PATH=firebase-service-account.json
```

Leaving `FIREBASE_CONFIG_PATH` empty turns notifications off. Setting it to a missing file stops the backend from starting.

If the Web API key is restricted to certain websites, add the live frontend's domain.

## Google sign-in

In Google Cloud, under **APIs & Services → Credentials**, add these as Authorized JavaScript origins of the OAuth client:

- `http://localhost:5173`
- `http://localhost:8080` (the all-in-one Docker image)
- `https://hostel-complaints-frontend.onrender.com`

The client ID must match `spring.security.oauth2.resourceserver.jwt.audiences` in `application.properties`.

## Render

Both services should deploy from the `main` branch. GitHub's default branch is `frontend`, so check this in **Settings → Build & Deploy**.

### Backend (Docker web service)

1. Use `backend` as the root directory, so Render finds the `Dockerfile`.
2. Add the backend environment variables.
3. Add a **Secret File** named `firebase-service-account.json`, then set:

   ```
   FIREBASE_CONFIG_PATH=/etc/secrets/firebase-service-account.json
   ```

Add the secret file before setting the path. The memory settings in the `Dockerfile` suit a 512 MB instance, and you can override them with `JAVA_OPTS`.

### Frontend (static site)

1. Use `frontend` as the root directory, `npm install && npm run build` as the build command, and `dist` as the publish directory.
2. Add the frontend environment variables.
3. Add a rewrite from `/*` to `/index.html`, so links like `/admin` work on reload.

If the frontend's address changes, update the allowed origins in `SecurityConfig` too.

## All-in-one Docker image

The root `Dockerfile` packages the whole app for running on any machine. Render doesn't use it.

| Part | In the image |
|---|---|
| Frontend | Built with `VITE_API_URL=/api` and served by the backend on port 8080 |
| Backend | Runs with the `docker` profile (`application-docker.properties`) |
| Database | PostgreSQL 16 inside the container, listening only there and needing no password. Hibernate creates the tables; `docker/setup.sql` adds the categories, `pg_trgm` and indexes; `docker/demo-data.sql` loads 10 demo complaints on first start |
| Photos, push notifications | Off: they need the project's keys, which aren't in the image |

**Building.** `frontend/.env` must be filled in. It's passed with `--secret`, so it's used for the frontend build only and isn't stored in the image. Its Google client ID and Firebase web config end up in the built JavaScript, as on the live site; they're public by design.

```bash
docker build --secret id=frontend_env,src=frontend/.env -t hostelfix:$(git rev-parse HEAD) .
```

**Running.**

```bash
docker run --rm -p 8080:8080 -e ADMIN_EMAILS=you@example.com hostelfix:<sha>
```

`ADMIN_EMAILS` decides who can use Staff Login. The data lasts until the container stops (`--rm`); the next start loads the demo data again.

**Sharing.** `docker save hostelfix:<sha> | gzip > hostelfix-<sha>.tar.gz`, then on the other machine `docker load -i hostelfix-<sha>.tar.gz`.

**Demo data.** Five students and four staff, all with made-up addresses, so nobody can sign in as them. The complaints cover every status, including an overdue work order, a nudged complaint, a rejection and upvoted community issues.

## Releasing

1. Run any new migrations in Supabase.
2. Add any new settings on Render.
3. Merge to `main`, and let the backend deploy before the frontend.
4. Check that:
   - `/health` on the backend responds
   - Student Login and Staff Login both work
   - `/firebase-messaging-sw.js` on the frontend shows JavaScript, not HTML

## Troubleshooting

### Local setup

| Problem | Cause and fix |
|---|---|
| Blank page, "Missing Firebase environment variables" in the console | `frontend/.env` is incomplete. Compare it with `.env.example`, then restart `make frontend` |
| Frontend build fails with "Missing Firebase values" | The same, in the build environment |
| Login tabs don't work after pulling | Restart the backend |
| Port 8080 is already in use | An old backend is still running. Stop it first |
| Tests fail with "Unresolved compilation problem" | The IDE wrote stale classes. Run `./mvnw clean` |
| Postgres tests fail or are skipped locally | They need Docker |

### Sign-in

| Problem | Cause and fix |
|---|---|
| "Unauthorized staff email" | The email isn't added as staff, or doesn't match exactly |
| Staff member is refused despite being added | Gmail ignores dots, but Google reports the address as created. Add their exact address |
| "Please use Staff Login." | Staff and admins can't use the Student tab |
| A new admin can't sign in | Add them to `ADMIN_EMAILS` and restart the backend |

### Staff and work orders

| Problem | Cause and fix |
|---|---|
| "Could not add this staff member" | Usually the `users` table doesn't allow the `STAFF` role. Run `schema_nov06.sql` |
| Staff management shows 0 but the table shows a name | The complaint was assigned before work orders existed. Assign it again |

### Notifications

Check the `make backend` output or Render logs first:

| Log line | Meaning |
|---|---|
| `Sent assignment notification ...` | Firebase accepted it, so look at the browser or device |
| `...the assignee has no registered device` | The person hasn't turned notifications on, or has logged out |
| `Could not send assignment notification ...` | Firebase refused it; the line gives the reason |
| `Firebase is not configured...` | `FIREBASE_CONFIG_PATH` isn't set |
| No line | Nothing needed sending: the person assigned themselves, or was already the assignee |
| `Sent nudge notification for complaint ...` | Firebase accepted the nudge. The line shows how many admins it reached |
| `No admin has push notifications on...` | The nudge was saved, but no admin has a registered device |

| Problem | Cause and fix |
|---|---|
| The in-app pop-up works, but no system notification | Windows notifications may be off: check Settings → System → Notifications, the browser's entry there, and Do Not Disturb |
| Nothing arrives on one device | Each user has one device, and the last one registered wins. Two accounts in one browser also share a device |
| Nothing in incognito | Notifications usually don't work there |
| Nothing on iPhone | The site must be added to the Home Screen first (iOS 16.4 or later) |
| Nothing on campus Wi-Fi | Some networks block push connections. Try a hotspot |
