# Rate limiting and tuning for a 512 MB server

| | |
|---|---|
| **Goal** | Limit how fast one user can file complaints, and keep the backend within Render's 512 MB memory limit. |
| **Date** | 2026-11-01 |
| **Pull requests** | #27 |
| **Commits** | `0082634` |

## Context

The backend had no rate limit and used default server settings.

## Prompt

```text
Context: We are hardening the HostelFix Spring Boot backend for production deployment. We need to implement rate limiting and tune the server for a 512MB RAM constrained environment (Render free tier).

Task 1: Rate Limiting (Bucket4j)

Read pom.xml and provide the exact dependency XML for bucket4j-core.

Create RateLimitFilter.java (or an interceptor) to restrict requests to the POST /api/complaints endpoint to a maximum of 5 requests per minute per user (extract user ID from the JWT/Auth context or IP address). Return 429 Too Many Requests when exceeded.

Task 2: Resource Tuning
Provide the exact properties to add to application.properties to optimize Tomcat and HikariCP for a 512MB RAM container. Include properties for server.tomcat.max-threads, spring.datasource.hikari.maximum-pool-size, and connection timeouts to prevent out-of-memory crashes under concurrent load.
```

## Constraints

- Bucket4j for rate limiting
- A 512 MB container
- No new infrastructure, such as Redis

## Refinements

- **The named library was out of date.** `bucket4j-core` has been frozen since 2024; the maintained per-Java-version artifact was used instead.
- **A setting in the prompt did not exist.** `server.tomcat.max-threads` was renamed in Spring Boot, so it would have been silently ignored. The current name, `server.tomcat.threads.max`, was used.
- **Two different 429 errors.** The app already returned 429 for the 5-open-complaints limit, so the rate limit adds a `Retry-After` header and the form tells the two apart.
- **Memory limits measured, not guessed.** The AI's first JVM setting, `MaxRAMPercentage=60`, could have pushed the total past 512 MB. After running the real jar under load, it was lowered to 40.

## Outcome

Measured memory use: 361 MB after startup and 377 MB after 500 concurrent requests.
