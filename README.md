# Distributed Job Orchestration Platform

A learning-oriented backend that models how SaaS-style platforms run **asynchronous jobs**: REST APIs, PostgreSQL for state, Apache Kafka for work queues, and clear job lifecycle handling (including retries and external callbacks).

## Features

- **Job lifecycle** — States include `PENDING`, `QUEUED`, `RUNNING`, `COMPLETED`, and `FAILED`.
- **Job types** — `EMAIL`, `REPORT`, and `EXTERNAL` (internal simulation vs outbound HTTP + callback).
- **API service** — Submit jobs, fetch status, retry failed jobs, and accept partner callbacks.
- **Kafka** — Jobs are published to a `jobs` topic; a dead-letter topic name is reserved for later use.
- **PostgreSQL** — Durable job records; schema managed with **Flyway** migrations.
- **Docker Compose** — Local PostgreSQL, ZooKeeper, and Kafka for development.

## Architecture (high level)

```text
Client  →  API (REST)  →  PostgreSQL
                ↓
            Kafka (jobs topic)
                ↓
         Worker service (planned)  →  handlers, DLQ, external calls
```

The **worker** module is the next major piece: it will consume from Kafka, execute handlers, and update job status in the database.

## Tech stack

| Area        | Choice |
|------------|--------|
| Language   | Java 17+ |
| Framework  | Spring Boot 3.x |
| Persistence | Spring Data JPA, PostgreSQL |
| Messaging  | Spring Kafka, Apache Kafka |
| Migrations | Flyway |
| Build      | Maven (multi-module) |
| Containers | Docker, Docker Compose |

## Repository layout

```text
├── common-lib/          # Shared enums, Kafka topic names, JobMessage DTO
├── api-service/       # REST API, Kafka producer, Flyway migrations
├── docker-compose.yml # Postgres (host port 5433), ZooKeeper, Kafka
├── pom.xml            # Parent POM
└── .env.example       # Template for local environment variables (copy to .env)
```

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker Desktop (or Docker Engine + Compose) for infrastructure

## Quick start

### 1. Configuration

Copy the environment template and edit values for your machine (never commit real secrets):

```bash
cp .env.example .env
```

Keep `.env` out of version control; it is listed in `.gitignore`.

### 2. Start infrastructure

From the repository root:

```bash
docker compose up -d postgres zookeeper kafka
docker compose ps
```

PostgreSQL is exposed on **host port `5433`** so it can run alongside another PostgreSQL instance on the default port `5432`.

### 3. Build the project

```bash
mvn -N install
mvn -pl common-lib install
mvn compile
```

### 4. Run the API

Either run `com.distributedjob.api.ApiServiceApplication` from your IDE, or:

```bash
mvn -pl api-service spring-boot:run
```

Ensure the same environment variables you use for the database (and optional callback secret) are available to the process—IDE run configuration or `source .env` in the shell before `mvn`, depending on your workflow.

Default HTTP port: **8080**.

### 5. Smoke test

Create a job (example body shape):

```http
POST /jobs
Content-Type: application/json

{
  "type": "EMAIL",
  "payloadJson": "{\"to\":\"user@example.com\"}"
}
```

Then `GET /jobs/{id}` using the returned `id`.

## HTTP API (overview)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/jobs` | Create a job; persists then enqueues to Kafka when the broker is available |
| `GET` | `/jobs/{id}` | Read job by UUID |
| `POST` | `/jobs/{id}/retry` | Re-queue a **failed** job |
| `POST` | `/jobs/callback` | External completion for **EXTERNAL** jobs in **RUNNING** state |

Callback requests may require header `X-Callback-Secret` when a callback secret is configured—see configuration below.

Errors are returned as **RFC 7807-style** problem details where applicable.

## Configuration

Application settings are in `api-service/src/main/resources/application.yml`. Sensitive values should come from the environment, not from committed files.

Typical variables (names only—set values locally):

| Variable | Role |
|----------|------|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | PostgreSQL connection (aligned with Docker Compose) |
| `CALLBACK_SECRET` | Optional shared secret for `X-Callback-Secret` on `/jobs/callback`; leave unset for local-only experiments |

Kafka bootstrap defaults to `localhost:9092` for local Compose.

## Database migrations

Flyway scripts live under `api-service/src/main/resources/db/migration/`. Hibernate is set to **validate** the schema against the entities—DDL is owned by migrations, not auto-update.

If you introduce Flyway after tables already existed without Flyway history, you may need a one-time clean baseline (empty DB or manual alignment). Fresh Compose volumes usually avoid that.

## Logging

The `com.distributedjob` logger is configured for **DEBUG** in `application.yml` to simplify troubleshooting; tune levels per package in the same file for quieter output.

## Roadmap

- **Worker service** — Kafka consumer, handler routing, retries, DLQ, external job execution
- **Optional** — Metrics, Redis, dashboard UI

## License

See [LICENSE](LICENSE) in this repository.
