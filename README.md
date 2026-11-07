# HostelFix

A complaint ticketing system for hostels, built for Bhadra hostel at IIT Madras (CS5013). Students report problems, hostel staff and admins assign and resolve them, and everyone can follow each complaint from filing to resolution.

## Features

**Students**
- Sign in with an `@smail.iitm.ac.in` Google account
- File room or community-area complaints, with an optional photo
- See a warning when a similar complaint already exists
- Follow each complaint's status and timeline, and see who is handling it
- Upvote community issues, and delete a complaint while it is still pending
- Get push notifications when a status changes

**Hostel staff**
- Sign in with any Google account an admin has added
- See all complaints, assign complaints to themselves, and work through them
- Use **My complaints** for their assigned and filed complaints
- File community-area or room complaints
- Get notified when a complaint is assigned to them
- View the analytics dashboard

**Admins**
- Everything staff can do, plus assigning complaints to anyone
- Add and remove staff, and see each person's workload

## Tech stack

| Part | Technology |
|---|---|
| Frontend | React 19, Vite, Tailwind CSS, installable as a PWA |
| Backend | Spring Boot 4, Java 21, Hibernate |
| Database and photos | Supabase (PostgreSQL and Storage) |
| Sign-in | Google Identity Services |
| Push notifications | Firebase Cloud Messaging |
| Hosting | Render |

## Quick start

You need Java 21, Node 22 or later, and `make`. On WSL, install Node with [nvm](https://github.com/nvm-sh/nvm).

1. Create the settings files and fill them in. [Environment variables](docs/DEPLOYMENT.md#environment-variables) explains each value.

   ```bash
   cp backend/.env.example backend/.env
   cp frontend/.env.example frontend/.env
   ```

2. Start each part in its own terminal, from the repository root:

   ```bash
   make backend    # http://localhost:8080
   make frontend   # http://localhost:5173
   ```

3. Open `http://localhost:5173`. Students use **Student Login**; admins and hostel staff use **Staff Login**.

The frontend will show a blank page if its Firebase settings are missing. See [Troubleshooting](docs/DEPLOYMENT.md#troubleshooting).

## Tests

```bash
cd backend && ./mvnw test     # Postgres-based tests need Docker
cd frontend && npm test
```

## Documentation

| Document | What it covers |
|---|---|
| [User guide](docs/USER_GUIDE.md) | How to use HostelFix as a student, staff member or admin |
| [Architecture](docs/ARCHITECTURE.md) | How the system fits together, and its main workflows |
| [API reference](docs/API.md) | Every endpoint |
| [Database](docs/DATABASE.md) | Tables and migrations |
| [Deployment](docs/DEPLOYMENT.md) | Supabase, Firebase and Render setup, settings and troubleshooting |
| [Frontend](frontend/README.md) | How the frontend code is organised |

## Docker

To run the backend in a container:

```bash
cd backend
docker build -t hostel-complaints-backend .
docker run -d --name hostel-complaints-backend -p 8080:8080 --env-file .env hostel-complaints-backend
curl http://localhost:8080/health
```
