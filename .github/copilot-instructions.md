# Copilot Instructions: ED Workflow Engine

## Build & Run Commands

```bash
# Run the application
./gradlew bootRun

# Build
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.ed.workflow.service.WorkflowEngineIntegrationTest"

# Run a single test method
./gradlew test --tests "com.ed.workflow.service.WorkflowEngineIntegrationTest.testCreateAndStartWorkflow"

# Clean build
./gradlew clean build
```

## Infrastructure (Local Dev)

Start PostgreSQL before running the app:
```bash
podman-compose up -d
# or: docker-compose up -d
```

Database: `workflowdb` on `localhost:5432`, credentials: `user`/`password`.

The app uses `spring.jpa.hibernate.ddl-auto: update` — Hibernate auto-manages schema. There are no Flyway/Liquibase migrations.

## Architecture Overview

**Stack:** Java 25, Spring Boot 4.0.2, PostgreSQL 15, Gradle (Kotlin DSL)

**Package layout** under `com.ed.workflow`:

```
controller/       REST API — request/response DTOs are Java Records defined as inner classes
service/          Business logic — WorkflowService (orchestration), AsyncStepExecutor (step execution), IdempotencyService, TimeoutMonitor
model/            JPA entities (WorkflowDefinition, WorkflowInstance, WorkflowExecutionLog) + WorkflowStatus enum
model/schema/     Non-persistent POJOs: WorkflowSchema, WorkflowStep (the in-memory blueprint)
repository/       Spring Data JPA repositories
```

### Core Flow

1. A **WorkflowDefinition** stores a JSON schema (`WorkflowSchema`) containing ordered `WorkflowStep` objects.
2. Starting a definition creates a **WorkflowInstance** (state machine: `PENDING → RUNNING → COMPLETED/FAILED`).
3. `AsyncStepExecutor` processes each step asynchronously (`@Async`). Steps are typed:
   - `service` — automated execution
   - `human` — pauses and waits for an API call to `completeHumanTask()`
   - `decision` — routes to next step using SpEL expressions evaluated against the instance's `context` (a JSONB blob)
   - `end` — terminates the workflow
4. A **context** map travels with each instance; steps write outputs into it for downstream steps.
5. `TimeoutMonitor` runs every 10 seconds (`@Scheduled`) to fail stalled steps.
6. Every state change is written to `WorkflowExecutionLog` for a full audit trail.

## Key Conventions

### DTOs as Inner Records
Request/response types live as static inner `record` classes inside the relevant `@RestController`. Do not create separate DTO files.

### Lombok Usage
All entities and service classes use Lombok: `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`. Use `@RequiredArgsConstructor` for constructor injection in services.

### Self-injection for Async
`AsyncStepExecutor` self-injects itself with `@Lazy @Autowired private AsyncStepExecutor self` to allow `@Async` proxy interception on recursive/internal calls. Maintain this pattern if adding async methods to the class.

### SpEL for Decision Logic
Decision step routing uses Spring Expression Language. Expressions are evaluated against the workflow instance's `context` map. Example: `"context['status'] == 'approved'"`.

### Idempotency Guard
Before executing any step, check `IdempotencyService` to confirm the step hasn't already completed. This prevents double-execution on retries.

### Retry & Backoff
Retry delay is computed as `baseDelay * 2^attemptNumber` (exponential backoff). Configuration is per-step in the `WorkflowStep` schema (`maxRetries`, `retryDelayMs`).

### Versioning
`WorkflowDefinition` has a `version` field. The unique constraint is on `(name, version)` — never modify an in-use definition; create a new version instead.

### API Versioning
- `/api/v2/workflows/**` — current API
- `/api/v1/workflows/**` — deprecated; keep backward compatible but do not add new features

### Test Profile
Integration tests use `@ActiveProfiles("test")` and `SpringBootTest.WebEnvironment.RANDOM_PORT`. Test DB config lives in `src/test/resources/application-test.yml`.
