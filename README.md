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
This repository currently provides core workflow definition management APIs.
Additional orchestration and execution features will be added in later phases.

## Implemented Features

### Workflow Definitions API

#### POST /definitions
Create a new workflow definition.

**Request:**
```json
{
  "workflowKey": "approval_workflow",
  "version": 1,
  "name": "Approval Workflow v1",
  "definitionJson": "{\"steps\": []}",
  "active": true
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "workflowKey": "approval_workflow",
  "version": 1,
  "name": "Approval Workflow v1",
  "definitionJson": "{\"steps\": []}",
  "active": true,
  "createdAt": "2026-03-19T12:00:00",
  "updatedAt": "2026-03-19T12:00:00"
}
```

**Status Codes:**
- `201 Created`: Workflow definition successfully created
- `400 Bad Request`: Invalid request body or validation failed
- `409 Conflict`: Workflow definition with the same key and version already exists

**Validation Rules:**
- `workflowKey`: Required, non-empty string
- `version`: Required, positive integer
- `name`: Required, non-empty string
- `definitionJson`: Required, non-empty string (must be valid JSON)
- `active`: Optional, defaults to true
- Combination of `workflowKey` and `version` must be unique

## Local Infrastructure
Use Podman Compose to start local dependencies:

```bash
docker-compose up -d
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
