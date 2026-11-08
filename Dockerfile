# syntax=docker/dockerfile:1
#
# HostelFix in one container, for running the whole app on any machine:
# the React frontend, the Spring Boot backend and a PostgreSQL database with demo data.
# It never connects to the project's real Supabase database or storage.
#
# Build (from the repository root, with frontend/.env filled in):
#   docker build --secret id=frontend_env,src=frontend/.env -t hostelfix .
# Run:
#   docker run --rm -p 8080:8080 -e ADMIN_EMAILS=you@example.com hostelfix
# then open http://localhost:8080
#
# The render deployment does not use this file; it uses backend/Dockerfile.

# ---- 1. Frontend ----
FROM node:22-bookworm-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# The page and the API share one address in this image. Set here, this wins over .env.
ENV VITE_API_URL=/api
# frontend/.env holds the Google client ID and the Firebase web config. These are public
# (every visitor's browser receives them), but the file is mounted only for this step,
# so it is never stored in the image.
RUN --mount=type=secret,id=frontend_env,target=/app/frontend/.env,required=true npm run build

# ---- 2. Backend, with the frontend inside it ----
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# ---- 3. Runtime: Java and PostgreSQL ----
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/lib/postgresql/data \
    && chown -R postgres:postgres /var/lib/postgresql
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
COPY docker/entrypoint.sh /usr/local/bin/hostelfix-start
RUN chmod +x /usr/local/bin/hostelfix-start

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-Xmx768m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["hostelfix-start"]
