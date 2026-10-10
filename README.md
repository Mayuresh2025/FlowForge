# FlowForge — Background Job Processing Platform

FlowForge is a full-stack learning project that demonstrates asynchronous job submission, a Redis-backed queue, PostgreSQL-backed job state, worker concurrency, retry handling, a React/TypeScript operations dashboard, Docker Compose, and API documentation.

> **Security / production note:** this initial version is intended for local development and portfolio demonstration. It does not yet implement user authentication/authorization, rate limiting, database migrations, distributed worker leases, or exponential retry backoff. Do not expose it publicly with real or sensitive data until those controls are added. The local PostgreSQL password is a development default only.

## Stack

- Java 21 + Spring Boot 3.5
- PostgreSQL 17
- Redis 7
- React + TypeScript + Vite
- Docker Compose
- Springdoc OpenAPI / Swagger UI

## Requirements

- Docker Desktop (with Docker Compose v2)
- Git (recommended)
- For running without Docker: Java 21, Maven 3.9+, Node.js 22+, PostgreSQL, Redis

## Run the complete app

From the `flowforge` project root:

```bash
docker compose up --build
```

Wait for the backend to finish starting, then open:

- Dashboard: http://localhost:3000
- API health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html

Stop the services with `Ctrl+C`, or run `docker compose down`. To also delete the local database and Redis data, run `docker compose down -v` (destructive).

## Supported job types

### ECHO
Returns the JSON payload as the result.

```json
{"type":"ECHO","payload":{"message":"hello"},"maxAttempts":3}
```

### WAIT
Waits for the specified number of milliseconds (0–30000), then returns a completion message.

```json
{"type":"WAIT","payload":{"milliseconds":1500},"maxAttempts":3}
```

### FAIL
Intentionally fails so you can inspect retry attempts and terminal failure behavior.

```json
{"type":"FAIL","payload":{"message":"test failure"},"maxAttempts":3}
```

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/jobs` | Submit a job |
| `GET` | `/api/jobs?page=0&size=50` | List recent jobs |
| `GET` | `/api/jobs/{id}` | Fetch one job |
| `POST` | `/api/jobs/{id}/cancel` | Cancel a queued job |
| `GET` | `/api/stats` | Read status counters |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui.html` | Interactive API documentation |

Example using curl:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"ECHO","payload":{"message":"hello FlowForge"},"maxAttempts":3}'
```

## How the queue works

1. The API validates the request and stores a `QUEUED` record in PostgreSQL.
2. A dispatcher periodically publishes IDs for queued jobs to a Redis list. PostgreSQL remains the durable source of truth, so queued work can be republished after a restart.
3. Two worker threads pop job IDs and only start records currently in `QUEUED` state.
4. Successful jobs store their result and become `SUCCEEDED`.
5. Failed jobs record an error and return to `QUEUED` until `maxAttempts` is reached; after that they become `FAILED`.
6. Duplicate queue entries can occur by design; workers re-check job state before starting work.

This is a practical starter architecture, not a guarantee of exactly-once side effects. For production-grade execution, add transactional outbox/inbox patterns or stronger queue coordination, expiring worker leases and recovery for stale `RUNNING` jobs, idempotency keys, retry backoff, and per-job execution history.

## Local development without Docker

### Backend

Start PostgreSQL and Redis locally, then from `backend/`:

```bash
mvn spring-boot:run
```

Defaults expect PostgreSQL at `localhost:5432` (database/user `flowforge`, password `flowforge_local_password`) and Redis at `localhost:6379`. Override them with environment variables as needed.

### Frontend

From `frontend/`:

```bash
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies API requests to `http://localhost:8080`.

## Tests and build

```bash
# backend tests
cd backend
mvn test

# frontend production build
cd ../frontend
npm install
npm run build
```

## Portfolio completion checklist

- [ ] Verify the dashboard can submit ECHO, WAIT, and FAIL jobs.
- [ ] Confirm job status changes without refreshing manually.
- [ ] Confirm the attempt limit works on FAIL jobs.
- [ ] Restart the backend and verify queued jobs are redispatched.
- [ ] Add integration tests with disposable PostgreSQL and Redis.
- [ ] Add authentication/authorization and rate limits before public exposure.
- [ ] Replace `ddl-auto: update` with versioned Flyway migrations.
- [ ] Add stale-worker recovery, backoff, metrics, and structured logging.
- [ ] Deploy frontend/API/database/Redis with private networking and TLS.
- [ ] Add screenshots, architecture diagram, benchmark results, and a demo video.

## License

Use and modify for learning and portfolio purposes.
