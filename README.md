# Distributed Job Orchestration Platform

A learning-oriented backend that models how SaaS-style platforms run **asynchronous jobs**: REST APIs, PostgreSQL for durable state, Apache Kafka for work distribution, and a separate **worker** process that executes jobs and updates lifecycle (including external webhooks and callbacks).

## Features

- **Job lifecycle** — `PENDING`, `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`.
- **Job types** — `EMAIL` and `REPORT` (handled inside the worker), `EXTERNAL` (outbound HTTP to a partner URL, then completion via `POST /jobs/callback`).
- **API service** — Create jobs, read status, retry failed jobs, accept external callbacks.
- **Worker service** — Kafka consumer; claims `QUEUED` jobs, runs handlers, writes status back to the **same** Postgres database as the API.
- **Kafka** — Work is published to the `jobs` topic (`jobs-dlq` is reserved for future dead-letter use).
- **PostgreSQL** — Single source of truth for job rows; schema owned by **Flyway** in `api-service` (worker validates only).
- **Docker Compose** — Local Postgres (host **5433**), ZooKeeper, and Kafka.

## Architecture (high level)

```text
Client  →  API (REST)  →  PostgreSQL
                ↓
            Kafka (jobs topic)
                ↓
         Worker service  →  EMAIL / REPORT / EXTERNAL handlers
```

The API persists jobs and enqueues work **only after** the row is `QUEUED`, so the worker never processes a Kafka message for a job that is still `PENDING` in the database.

## Tech stack

| Area         | Choice |
|-------------|--------|
| Language    | Java 17+ |
| Framework   | Spring Boot 3.x |
| Persistence | Spring Data JPA, PostgreSQL |
| Messaging   | Spring Kafka, Apache Kafka |
| Migrations  | Flyway (in `api-service`) |
| Build       | Maven (multi-module) |
| Containers  | Docker, Docker Compose |

## Repository layout

```text
├── common-lib/          # Shared enums, Kafka topic names, JobMessage DTO
├── api-service/         # REST API, Kafka producer, Flyway migrations
├── worker-service/      # Kafka consumer + job execution (same DB as API)
├── scripts/             # Manual E2E: e2e-smoke.sh
├── docker-compose.yml   # Postgres (5433), ZooKeeper, Kafka
├── pom.xml              # Parent POM
└── .env.example         # Copy to .env for local secrets / DB credentials
```

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker Desktop (or Docker Engine + Compose) for local infrastructure

## Quick start

### 1. Configuration

```bash
cp .env.example .env
```

Edit `.env` for your machine. Keep it out of version control.

### 2. Start infrastructure

```bash
docker compose up -d postgres zookeeper kafka
docker compose ps
```

Postgres listens on **host port 5433** (inside the container it is still `5432`).

### 3. Build and test

From the repository root:

```bash
mvn clean verify
```

This compiles all modules and runs **unit tests** (including the worker’s `JobExecutionService` tests).

### 4. Run the API

IDE: `com.distributedjob.api.ApiServiceApplication`, or:

```bash
mvn -pl api-service spring-boot:run
```

Use the same `POSTGRES_*` (and optional `CALLBACK_SECRET`) as in Compose / `.env` — e.g. `source .env` before `mvn` if you rely on env vars.

Default port: **8080**. Settings: `api-service/src/main/resources/application.yml`.

### 5. Run the worker

IDE: `com.distributedjob.worker.WorkerServiceApplication`, or:

```bash
mvn -pl worker-service spring-boot:run
```

Use the **same** database credentials and Kafka bootstrap as the API (`localhost:9092` with the provided Compose file). Default port: **8081**. Settings: `worker-service/src/main/resources/application.yml` (Flyway is **disabled** in the worker; migrations run from the API).

### 6. Try a single job (Postman or curl)

```http
POST /jobs
Content-Type: application/json

{
  "type": "EMAIL",
  "payloadJson": "{\"to\":\"user@example.com\"}"
}
```

Then `GET /jobs/{id}` with the returned `id`. With both services up, an `EMAIL` job should reach `COMPLETED` after the worker consumes the message.

### 7. Manual E2E script (all job types)

With Compose + **api-service** + **worker-service** running:

```bash
bash scripts/e2e-smoke.sh
```

| Variable        | Purpose |
|----------------|---------|
| `API_URL`       | API base URL (default `http://127.0.0.1:8080`) |
| `MAX_WAIT_SEC`  | Poll timeout per step (default `60`) |
| `WEBHOOK_URL`   | URL the worker POSTs to for `EXTERNAL` jobs (default `https://httpbin.org/post`; worker needs outbound HTTPS) |
| `CALLBACK_SECRET` | If set on the API (`job.callback.secret`), export the same value so `/jobs/callback` succeeds |

The script runs **EMAIL**, **REPORT**, **EXTERNAL** (callback `COMPLETED`), and **EXTERNAL** (callback `FAILED`).

## HTTP API (overview)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/jobs` | Create job: persist `PENDING` → `QUEUED`, **then** publish to Kafka |
| `GET` | `/jobs/{id}` | Read job by UUID |
| `POST` | `/jobs/{id}/retry` | Re-queue a **failed** job (increments retries, publishes after `QUEUED` is saved) |
| `POST` | `/jobs/callback` | Partner completion for **EXTERNAL** jobs in **RUNNING** (`COMPLETED` or `FAILED`) |

If `job.callback.secret` is set, send header `X-Callback-Secret` on `/jobs/callback`.

## Configuration

| Location | Notes |
|----------|--------|
| `api-service/src/main/resources/application.yml` | Datasource, Kafka producer, `job.callback.secret` |
| `worker-service/src/main/resources/application.yml` | Datasource, Kafka consumer, webhook timeouts |

Environment variables commonly mirror Docker Compose (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `CALLBACK_SECRET`).

Kafka bootstrap defaults to `localhost:9092` for local Compose.

## Database migrations

Flyway SQL lives under `api-service/src/main/resources/db/migration/`. Hibernate uses **validate**; DDL is not auto-generated in production paths.

## Logging

`com.distributedjob` is set to **DEBUG** in the bundled `application.yml` files for easier local tracing; tighten per package as needed.

## Roadmap

- **Worker hardening** — DLQ publishing, richer error/detail in DB, timeouts/retries policy for `EXTERNAL`
- **Optional** — Metrics, Redis, dashboard UI

## License

See [LICENSE](LICENSE) in this repository.
