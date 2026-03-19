# Workflow Engine

## Tech Stack
- Spring Boot 4.0.3
- Java 21
- Gradle (Kotlin DSL)
- PostgreSQL (primary persistence)
- Flyway (database migration)
- Redis (cache)
- Apache Kafka (messaging)
- HashiCorp Consul (external configuration)
- Lombok
- Springdoc OpenAPI
- Testcontainers

## Why PostgreSQL for Persistence
A workflow engine requires strong consistency for workflow state transitions, retries, idempotency, and audit logs. PostgreSQL is the best fit for the first iteration because it provides:
- ACID transactions for reliable orchestration state changes.
- Strong querying and indexing for workflow instance tracking.
- Mature operational tooling and backup strategies.
- Native JSONB support for flexible workflow context payloads.

## Project Scope (Current)
This repository is currently scaffolded only.
Business workflow orchestration logic will be added in a later phase.

## Local Infrastructure
Use Podman Compose to start local dependencies:

```bash
podman-compose up -d
```

Services:
- PostgreSQL: localhost:5432
- Redis: localhost:6379
- Kafka: localhost:9092
- Consul: localhost:8500

## API Docs
After starting the application:
- OpenAPI: /api-docs
- Swagger UI: /swagger-ui.html

## Notes
- Flyway is enabled and runs migration scripts from classpath:db/migration.
- Migration naming convention: `V<yyyyMMddHHmmss>__<description>.sql`.
- Application reads external configuration from Consul via optional import.
- Testcontainers is configured in the test scope for infrastructure-backed tests.
