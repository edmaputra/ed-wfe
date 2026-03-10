# ED Workflow Engine — Feature Reference

This document describes the features of the workflow engine, covering what is currently implemented and what is planned for future development.

---

## Table of Contents

- [Status Legend](#status-legend)
- [A. Workflow Definition](#a-workflow-definition)
- [B. Execution Engine](#b-execution-engine)
- [C. Error Handling & Reliability](#c-error-handling--reliability)
- [D. Visibility & Monitoring](#d-visibility--monitoring)
- [E. Architecture & Infrastructure](#e-architecture--infrastructure)
- [API Reference](#api-reference)
- [Planned Feature Designs](#planned-feature-designs)
  - [P1. Worker Pattern & Message Queue Decoupling](#p1-worker-pattern--message-queue-decoupling)
  - [P2. Monitoring Dashboard](#p2-monitoring-dashboard)
  - [P3. Startup Recovery for In-Progress Instances](#p3-startup-recovery-for-in-progress-instances)
  - [P4. YAML Schema Support](#p4-yaml-schema-support)
  - [P5. Database Migration Management](#p5-database-migration-management)
  - [P6. Workflow Lifecycle Controls](#p6-workflow-lifecycle-controls)

---

## Status Legend

| Badge | Meaning |
|-------|---------|
| ✅ Implemented | Available in the current codebase |
| ⚙️ Partial | Partially implemented or simulated |
| 🔲 Planned | Designed but not yet implemented |

---

## A. Workflow Definition

### ✅ Schema-Based Definitions (JSON)

Workflows are defined using a JSON schema. No task logic is hardcoded in the engine.

**Schema structure:**
```json
{
  "version": "1.0",
  "startStep": "step-1",
  "steps": [
    {
      "id": "step-1",
      "type": "service",
      "next": "step-2",
      "params": {},
      "timeout": 30000
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `version` | String | Schema format version |
| `startStep` | String | ID of the first step to execute |
| `steps` | List | Ordered list of `WorkflowStep` definitions |

### ✅ Step Types

Each step has a `type` that determines execution behaviour.

#### `service` — Automated Task
Represents an automated step executed by the engine.

| Param | Type | Description |
|-------|------|-------------|
| `retries` | Integer | Max retry attempts (default: 3) |
| _(custom)_ | Any | User-defined params passed to worker |

> ⚙️ **Note:** Service task execution is currently **simulated** with a 1-second delay. Real external service/API calls via a worker pattern are [planned](#-worker-pattern--message-queue-decoupling).

#### `human` — Manual Approval Task
Pauses workflow execution and waits for an external actor to call the human-actions API.

| Param | Type | Description |
|-------|------|-------------|
| `actions` | `Map<String, String>` | Maps action names to the next step ID |

**Example:**
```json
{
  "id": "approval",
  "type": "human",
  "params": {
    "actions": {
      "approve": "process-payment",
      "reject": "notify-rejection"
    }
  }
}
```

When no action is provided to the API, the engine defaults to `"complete"` and falls back to `step.next`.

#### `decision` — Conditional Branching
Evaluates rules using Spring Expression Language (SpEL) against the workflow's context and routes to the matching step.

| Param | Type | Description |
|-------|------|-------------|
| `rules` | `List<Map>` | Ordered list of `{condition, next}` pairs |

Each rule:
| Field | Type | Description |
|-------|------|-------------|
| `condition` | String | SpEL expression, `"default"`, or `"true"` |
| `next` | String | Target step ID if condition evaluates to `true` |

**Example:**
```json
{
  "id": "check-amount",
  "type": "decision",
  "params": {
    "rules": [
      { "condition": "#context['amount'] > 1000", "next": "require-approval" },
      { "condition": "default", "next": "auto-approve" }
    ]
  }
}
```

> Rules are evaluated in order. The first matching rule wins. SpEL context is available as `#context['key']`.

#### `end` — Workflow Termination
Marks the workflow as `COMPLETED`. No `next` step or `params` required.

```json
{ "id": "finish", "type": "end" }
```

### ✅ Definition Versioning

Every workflow definition is identified by `name` + `version`. The `(name, version)` pair is unique in the database.

- When a new definition is registered with an existing name, the version is automatically incremented.
- Running instances remain pinned to the definition version they were started with.
- **Never modify an in-use definition** — always register a new version.

### 🔲 YAML Schema Support

Workflow definitions currently only accept JSON. YAML as an alternative schema format is planned.

---

## B. Execution Engine

### ✅ Workflow Instance & State Machine

Starting a workflow creates a `WorkflowInstance` that tracks execution state.

**States:**

```
PENDING ──► RUNNING ──► COMPLETED
                  │
                  └──► FAILED
```

| Status | Description |
|--------|-------------|
| `PENDING` | Created but not yet started |
| `RUNNING` | Currently executing a step |
| `COMPLETED` | All steps finished successfully |
| `FAILED` | Step exceeded retries or timed out |

> Currently, instances are created in `RUNNING` state and begin execution immediately. `PENDING` is defined in the enum but reserved for future queuing support.

### ✅ Asynchronous Step Execution

Steps are executed asynchronously on a Spring-managed thread pool (`@Async`). This allows multiple workflow instances to execute concurrently without blocking the HTTP request thread.

### ✅ Context / Variables

A `context` map (stored as JSONB) travels with each workflow instance. It allows steps to pass data to downstream steps.

- The initial context is provided when starting a workflow via the `POST /start` API.
- Human task completions can merge additional `contextUpdates` into the context.
- Decision steps read from `context` to evaluate routing rules.

### ✅ Persistence & Restart Recovery

All workflow state is persisted to PostgreSQL. If the application restarts:

- All `WorkflowInstance` records survive.
- The `TimeoutMonitor` will detect stalled `RUNNING` instances on next cycle.

> ⚙️ **Limitation:** Stalled `RUNNING` instances after a restart are only detected as timed out if their step timeout elapses. There is no automatic re-queuing of in-progress steps on startup.

### ✅ Idempotency

Before executing any step, the engine checks `WorkflowExecutionLog` to see if the step has already emitted a `COMPLETED` event. If it has, execution is skipped.

- Applies to: `service` and `human` step types.
- Excluded: `decision` steps are always re-evaluated (stateless by design).

This prevents double-execution when retries occur (e.g., no double payments or duplicate records).

---

## C. Error Handling & Reliability

### ✅ Automatic Retries with Exponential Backoff

When a `service` step throws an exception, the engine automatically retries it.

| Config | Source | Default |
|--------|--------|---------|
| Max retries | `step.params["retries"]` | `3` |
| Base delay | Hardcoded | `1000ms` |

Retry delay formula: `1000ms × 2^(attempt - 1)`

| Attempt | Delay |
|---------|-------|
| 1st retry | 1s |
| 2nd retry | 2s |
| 3rd retry | 4s |
| ... | ... |

Each retry attempt is recorded in the audit log as `RETRY_SCHEDULED`. After all retries are exhausted, the instance is marked `FAILED`.

### ✅ Step Timeouts

Each step can define a `timeout` in milliseconds. The `TimeoutMonitor` checks all `RUNNING` instances every **10 seconds**. If the time elapsed since `updatedAt` exceeds the step's timeout:

- A `TIMEOUT` event is written to the audit log.
- The instance is marked `FAILED`.

```json
{ "id": "call-payment", "type": "service", "timeout": 30000 }
```

> Setting `timeout` to `0` or omitting it disables timeout checking for that step.

### ✅ Global Error Responses (RFC 9457)

All API errors are returned as [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457):

| Scenario | HTTP Status | `type` |
|----------|-------------|--------|
| Validation failure (`@Valid`) | 400 | `/problems/constraint-violation` |
| Bad argument (e.g., definition not found) | 400 | `about:blank` |
| Invalid workflow state (e.g., human action on non-human step) | 409 | `about:blank` |
| Unexpected server error | 500 | `about:blank` |

---

## D. Visibility & Monitoring

### ✅ Audit Logs

Every state change in a workflow is recorded as a `WorkflowExecutionLog` entry with a timestamp.

**Log event types:**

| Action | Trigger |
|--------|---------|
| `STARTED` | Step begins execution |
| `COMPLETED` | Step finishes successfully |
| `WAITING` | Human task is awaiting external action |
| `HUMAN_{ACTION}` | Human task completed (e.g., `HUMAN_APPROVE`, `HUMAN_REJECT`) |
| `RETRY_SCHEDULED` | Step failed, retry queued |
| `FAILED` | Step failed after all retries exhausted |
| `TIMEOUT` | Step exceeded its configured timeout |
| `EVALUATING` | Decision step is evaluating its rules |
| `UNKNOWN` | Unrecognised step type encountered |

Logs are retrievable in chronological order via `GET /api/workflows/logs/{instanceId}`.

### 🔲 Monitoring Dashboard

A dashboard to give operational visibility into workflow health is planned. It would expose:

- Count of workflows by status (`RUNNING`, `FAILED`, `COMPLETED`)
- List of currently stuck or failed instances
- Step-level throughput and latency metrics

This could be built as:
- A dedicated `/api/admin/dashboard` endpoint
- Integration with Spring Boot Actuator + Micrometer metrics
- An external monitoring dashboard (e.g., Grafana)

---

## E. Architecture & Infrastructure

### ✅ Definition Versioning Strategy

Instances are pinned to the `WorkflowDefinition.id` (which encodes `name + version`) at creation time. Updating a workflow definition does not affect running instances — they continue executing against the version they started with.

### ✅ Decoupled Execution (In-Process)

The engine does not contain task-specific business logic. Step types are generic (`service`, `human`, `decision`, `end`), and step behaviour is configured via `params`.

### 🔲 Worker Pattern / Message Queue Decoupling

Currently, `service` steps are executed **in-process** (simulated with a 1-second sleep). The planned architecture would decouple step execution fully:

1. Engine publishes a task message to a queue (e.g., Kafka, RabbitMQ) with step params.
2. An external worker service consumes the message, performs the actual work, and calls back the engine API with the result.
3. The engine advances the workflow on callback.

This would provide:
- True decoupling of business logic from the engine
- Horizontal scalability of workers
- Support for long-running tasks without blocking engine threads

### ✅ Containerised Local Development

PostgreSQL is provisioned via `podman-compose.yml`:

```bash
podman-compose up -d   # Start PostgreSQL
podman-compose down    # Stop
```

| Property | Value |
|----------|-------|
| Image | `postgres:15` |
| Database | `workflowdb` |
| Port | `5432` |
| Username | `user` |
| Password | `password` |

### 🔲 Database Migration Management

Schema is currently managed by `hibernate.ddl-auto: update`. A proper migration tool (Flyway or Liquibase) is needed before production to:

- Track schema change history
- Support safe rollbacks
- Prevent accidental destructive schema changes

---

## API Reference

### Base URL

`http://localhost:8080/api/workflows`

---

### POST `/definitions/v2` — Register Workflow Definition

Registers a new workflow definition. If a definition with the same `name` already exists, a new version is auto-incremented.

**Request:**
```json
{
  "name": "order-approval",
  "schemaJson": "{\"version\":\"1.0\",\"startStep\":\"review\",\"steps\":[...]}"
}
```

**Response `200`:**
```json
{
  "id": "uuid",
  "name": "order-approval",
  "version": 1,
  "createdAt": "2025-01-01T10:00:00"
}
```

> `POST /definitions` (v1) is deprecated. Use `/definitions/v2`.

---

### POST `/start` — Start Workflow Instance

Starts a new instance using the **latest version** of the named definition.

**Request:**
```json
{
  "name": "order-approval",
  "context": {
    "orderId": "ORD-123",
    "amount": 1500.00
  }
}
```

**Response `200`:**
```json
{
  "id": "uuid",
  "workflowName": "order-approval",
  "workflowVersion": 1,
  "status": "RUNNING",
  "currentStep": "review",
  "createdAt": "2025-01-01T10:00:00",
  "updatedAt": "2025-01-01T10:00:00"
}
```

---

### POST `/{instanceId}/human-actions` — Complete Human Task

Resumes a workflow instance paused on a `human` step.

**Request:**
```json
{
  "action": "approve",
  "contextUpdates": {
    "approvedBy": "manager@example.com"
  }
}
```

- `action`: Optional. Defaults to `"complete"` if omitted.
- `contextUpdates`: Optional. Merged into the instance context before proceeding.

**Response `200`:** Same shape as Start Workflow response.

**Errors:**
- `409` if the current step is not a `human` type.
- `400` if the instance is not found.

---

### GET `/logs/{instanceId}` — Get Audit Log

Returns all execution log entries for an instance in chronological order.

**Response `200`:**
```json
[
  {
    "id": "uuid",
    "stepId": "review",
    "action": "STARTED",
    "message": "...",
    "timestamp": "2025-01-01T10:00:01"
  },
  {
    "id": "uuid",
    "stepId": "review",
    "action": "WAITING",
    "message": "Waiting for human interaction",
    "timestamp": "2025-01-01T10:00:01"
  }
]
```

---

## Feature Summary

| Feature | Status |
|---------|--------|
| JSON schema-based workflow definitions | ✅ Implemented |
| YAML schema support | 🔲 Planned |
| Service step type | ⚙️ Partial (simulated) |
| Human step type with action routing | ✅ Implemented |
| Decision step type with SpEL | ✅ Implemented |
| End step type | ✅ Implemented |
| State machine (PENDING / RUNNING / COMPLETED / FAILED) | ✅ Implemented |
| Asynchronous step execution | ✅ Implemented |
| Workflow context (variables) | ✅ Implemented |
| PostgreSQL persistence | ✅ Implemented |
| Restart recovery | ⚙️ Partial (timeout-based detection only) |
| Definition versioning | ✅ Implemented |
| Idempotency guard | ✅ Implemented |
| Automatic retries with exponential backoff | ✅ Implemented |
| Step timeouts | ✅ Implemented |
| Audit logging | ✅ Implemented |
| RFC 9457 error responses | ✅ Implemented |
| Monitoring dashboard | 🔲 Planned |
| Worker pattern / message queue decoupling | 🔲 Planned |
| Startup recovery for in-progress instances | 🔲 Planned |
| Workflow lifecycle controls (pause, resume, cancel) | 🔲 Planned |
| Database migration tooling (Flyway/Liquibase) | 🔲 Planned |

---

## Planned Feature Designs

This section details the design intent for each planned feature to guide implementation.

---

### P1. Worker Pattern & Message Queue Decoupling

#### Problem

Service step execution is currently **in-process** and simulated. Real workflows need to call external systems (payment gateways, inventory services, email providers, etc.). Running these calls inside the engine tightly couples business logic to the engine and blocks threads during long-running operations.

#### Proposed Design

Introduce a **publish/callback model** where the engine delegates task execution to external workers via a message queue.

```
┌─────────────────────────────────────────────────────────────────┐
│                       Workflow Engine                           │
│                                                                 │
│  executeStep()                                                  │
│      │                                                          │
│      ▼  (service step)                                          │
│  Publish TaskMessage ──────────────────────────────────────────►│ Message Queue
│      │                                                          │ (Kafka / RabbitMQ)
│      │  Set status = WAITING_FOR_WORKER                         │
│      ▼                                                          │
│  (paused — awaiting callback)                                   │
│                                                                 │
│  POST /steps/{stepId}/complete  ◄───────── Worker callback      │
│      │                                                          │
│      ▼                                                          │
│  Merge result into context                                      │
│  Advance to next step                                           │
└─────────────────────────────────────────────────────────────────┘
         ▲                              │
         │                             ▼
    Worker Service              Worker Service
    (consumes task)             performs actual work,
                                calls back engine API
```

#### Message Schema

The engine publishes the following message to the queue when a `service` step begins:

```json
{
  "instanceId": "uuid",
  "stepId": "call-payment",
  "taskType": "PAYMENT_CHARGE",
  "params": {
    "amount": 1500.00,
    "currency": "USD"
  },
  "context": {
    "orderId": "ORD-123"
  },
  "callbackUrl": "http://engine-host/api/workflows/{instanceId}/steps/{stepId}/complete"
}
```

#### New Workflow State

A new status `WAITING_FOR_WORKER` (or reuse `RUNNING`) is needed to distinguish between "actively executing" and "waiting for an external worker to respond". Timeouts already cover the case where workers never reply.

#### New API Endpoint (Worker Callback)

```
POST /api/workflows/{instanceId}/steps/{stepId}/complete
```

**Request:**
```json
{
  "status": "SUCCESS",
  "output": {
    "transactionId": "TXN-456",
    "chargedAt": "2025-01-01T10:05:00"
  }
}
```

- `status`: `"SUCCESS"` or `"FAILURE"`
- `output`: Merged into the instance context before advancing to the next step
- On `"FAILURE"`: triggers existing `handleStepFailure()` retry logic

#### Engine Changes Required

| Component | Change |
|-----------|--------|
| `AsyncStepExecutor` | Replace simulated sleep in `executeServiceTask()` with queue publish |
| `WorkflowInstance` | Optionally add `WAITING_FOR_WORKER` to `WorkflowStatus` enum |
| New: `WorkerCallbackController` | New `POST /{instanceId}/steps/{stepId}/complete` endpoint |
| New: `TaskPublisher` | Interface + implementation for Kafka/RabbitMQ publishing |
| `build.gradle.kts` | Add `spring-boot-starter-amqp` or `spring-kafka` dependency |
| `podman-compose.yml` | Add RabbitMQ or Kafka + Zookeeper services |

#### Recommended Queue

**RabbitMQ** is recommended for the initial implementation:
- Simpler operational setup than Kafka
- Built-in dead-letter queues for failed messages
- Spring Boot auto-configuration via `spring-boot-starter-amqp`

Migrate to **Kafka** if high-throughput event streaming is later required.

#### Idempotency Consideration

Workers must be idempotent — the engine may re-deliver a message on retry. Workers should use the `instanceId + stepId` pair as a deduplication key.

---

### P2. Monitoring Dashboard

#### Problem

There is currently no way to get an operational view of the system. Operators cannot see how many workflows are running, which are stuck, or what the failure rate is without querying the database directly.

#### Proposed Design

Expose a dedicated set of read-only admin API endpoints, and optionally integrate with Spring Boot Actuator for metrics.

##### A. Admin REST API

New base path: `GET /api/admin/dashboard`

**Summary endpoint:**
```
GET /api/admin/dashboard/summary
```
```json
{
  "running":   12,
  "completed": 1040,
  "failed":    3,
  "pending":   0,
  "totalToday": 55
}
```

**Failed instances:**
```
GET /api/admin/instances?status=FAILED&page=0&size=20
```
```json
{
  "content": [
    {
      "id": "uuid",
      "workflowName": "order-approval",
      "workflowVersion": 2,
      "status": "FAILED",
      "currentStep": "call-payment",
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "totalElements": 3,
  "page": 0,
  "size": 20
}
```

**Instance search / filter:**
```
GET /api/admin/instances?status=RUNNING&name=order-approval&page=0&size=20
```

##### B. Spring Boot Actuator + Micrometer Metrics

Enable the existing `spring-boot-starter-actuator` (already a natural dependency) and register custom `Gauge` and `Counter` metrics via `MeterRegistry`:

| Metric Name | Type | Description |
|-------------|------|-------------|
| `workflow.instances.running` | Gauge | Current count of RUNNING instances |
| `workflow.instances.failed` | Gauge | Current count of FAILED instances |
| `workflow.step.executions.total` | Counter | Total step executions (tagged by `stepType`) |
| `workflow.step.failures.total` | Counter | Total step failures (tagged by `stepType`) |
| `workflow.step.duration.ms` | Timer | Step execution duration (tagged by `stepId`) |

These metrics can be scraped by **Prometheus** and visualised in **Grafana** using a workflow-specific dashboard.

##### C. Engine Changes Required

| Component | Change |
|-----------|--------|
| New: `DashboardController` | `GET /api/admin/...` endpoints |
| New: `DashboardService` | Queries `WorkflowInstanceRepository` with status filters and pagination |
| `WorkflowInstanceRepository` | Add `findByStatus(WorkflowStatus, Pageable)` and `countByStatus(WorkflowStatus)` |
| New: `WorkflowMetrics` | `@Component` that registers Micrometer gauges/counters; updated by `WorkflowService` and `AsyncStepExecutor` |
| `build.gradle.kts` | Add `spring-boot-starter-actuator` and `micrometer-registry-prometheus` |
| `podman-compose.yml` | Optionally add Prometheus + Grafana services |

---

### P3. Startup Recovery for In-Progress Instances

#### Problem

If the application crashes or is restarted while steps are executing, those `WorkflowInstance` records remain in `RUNNING` status with no active thread processing them. Currently, they will only be marked `FAILED` when the `TimeoutMonitor` detects the step timeout has elapsed — which may take minutes, or never if the step has no timeout configured.

#### Proposed Design

On application startup, scan for orphaned `RUNNING` instances and re-queue their current step for execution.

##### Recovery Flow

```
Application starts
      │
      ▼
ApplicationReadyEvent listener fires
      │
      ▼
Query: SELECT * FROM workflow_instances WHERE status = 'RUNNING'
      │
      ├── For each instance:
      │       ├── Has a currentStep?
      │       │       ├── YES → Re-submit to AsyncStepExecutor.executeStep()
      │       │       └── NO  → Mark as FAILED (inconsistent state)
      │       └── Log RECOVERY event to audit log
      │
      ▼
Normal operation resumes
```

##### Audit Log Event

A new `RECOVERY` action should be added to the log to make restarts traceable:

```json
{
  "stepId": "call-payment",
  "action": "RECOVERY",
  "message": "Step re-queued after application restart",
  "timestamp": "..."
}
```

##### Edge Cases

| Scenario | Handling |
|----------|----------|
| Step was already completed before crash | Idempotency check in `executeStep()` skips re-execution |
| Step has no timeout and never completes | Worker callback (P1) or manual cancellation (P6) needed |
| Multiple engine instances (horizontal scaling) | Requires distributed locking (e.g., `ShedLock`) to prevent double re-queue |

##### Engine Changes Required

| Component | Change |
|-----------|--------|
| New: `StartupRecoveryService` | `@Component` with `@EventListener(ApplicationReadyEvent.class)` |
| `WorkflowInstanceRepository` | `findByStatus(WorkflowStatus.RUNNING)` already exists |
| `WorkflowExecutionLog` | Add `RECOVERY` to log action vocabulary |

---

### P4. YAML Schema Support

#### Problem

Workflow schemas are currently submitted as a raw JSON string (`schemaJson`). JSON is verbose and error-prone to write by hand. YAML is more readable for defining multi-step workflows.

#### Proposed Design

Accept both JSON and YAML when registering a workflow definition. The engine normalises to JSON internally for storage.

##### API Change

The `POST /definitions/v2` endpoint should accept a `Content-Type` header to indicate format, or auto-detect the format of the `schemaJson` field:

**Option A — Auto-detect (Recommended):** Attempt JSON parse first; fall back to YAML parse. The field remains named `schemaJson` (or rename to `schema`).

**Option B — Separate field:** Add a `schemaFormat` field (`"json"` or `"yaml"`).

##### YAML Example

```yaml
version: "1.0"
startStep: review-order
steps:
  - id: review-order
    type: human
    params:
      actions:
        approve: charge-payment
        reject: notify-rejection
  - id: charge-payment
    type: service
    next: notify-success
    timeout: 30000
  - id: notify-success
    type: end
  - id: notify-rejection
    type: end
```

##### Engine Changes Required

| Component | Change |
|-----------|--------|
| `WorkflowService.createDefinition()` | Try Jackson JSON parse; on failure try `ObjectMapper` with `YAMLFactory`; store normalised JSON |
| `build.gradle.kts` | Add `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` dependency |
| `GlobalExceptionHandler` | Return a clear `400` error if neither JSON nor YAML can be parsed |

---

### P5. Database Migration Management

#### Problem

`hibernate.ddl-auto: update` is unsafe in production. It can silently fail to apply changes, cannot drop columns, and provides no migration history or rollback capability.

#### Proposed Design

Replace Hibernate DDL management with **Flyway**.

##### Migration File Structure

```
src/main/resources/db/migration/
├── V1__create_workflow_definitions.sql
├── V2__create_workflow_instances.sql
├── V3__create_workflow_execution_logs.sql
└── V4__add_worker_callback_token.sql   ← future migrations
```

##### Initial Migration (`V1` — `V3`)

These would capture the current schema as-is from the existing Hibernate-managed tables:

```sql
-- V1__create_workflow_definitions.sql
CREATE TABLE workflow_definitions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    version    INTEGER NOT NULL,
    schema_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_definition_name_version UNIQUE (name, version)
);

-- V2__create_workflow_instances.sql
CREATE TABLE workflow_instances (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definitions(id),
    status                VARCHAR(50) NOT NULL,
    current_step          VARCHAR(255),
    retry_count           INTEGER NOT NULL DEFAULT 0,
    context               TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

-- V3__create_workflow_execution_logs.sql
CREATE TABLE workflow_execution_logs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instances(id),
    step_id              VARCHAR(255),
    action               VARCHAR(100) NOT NULL,
    message              TEXT,
    timestamp            TIMESTAMP NOT NULL DEFAULT now()
);
```

##### Configuration Change

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # Hibernate validates schema matches entities; Flyway owns DDL
  flyway:
    enabled: true
    locations: classpath:db/migration
```

##### Engine Changes Required

| Component | Change |
|-----------|--------|
| `build.gradle.kts` | Add `org.flywaydb:flyway-core` and `org.flywaydb:flyway-database-postgresql` |
| `application.yml` | Change `ddl-auto: update` → `ddl-auto: validate`, add `flyway` config block |
| `src/main/resources/db/migration/` | Create `V1`, `V2`, `V3` migration files matching current schema |
| `application-test.yml` | Set `flyway.locations` to include test-specific seeds if needed |

---

### P6. Workflow Lifecycle Controls

#### Problem

Once a workflow instance is started, there is no way to manually intervene — operators cannot pause a running workflow, resume a paused one, or cancel one that is stuck or no longer needed.

#### Proposed Design

Introduce explicit lifecycle control endpoints.

##### New States

```
              ┌──────────────────┐
              │                  │
PENDING ──► RUNNING ──► COMPLETED│
              │    ▲              │
              │    │              │
              ▼    │              │
           PAUSED──┘              │
              │                  │
              ▼                  │
           CANCELLED             │
              │                  │
           FAILED ───────────────┘
```

| New Status | Description |
|------------|-------------|
| `PAUSED` | Manually suspended; no steps will execute until resumed |
| `CANCELLED` | Permanently stopped; cannot be resumed |

##### New API Endpoints

```
POST /api/workflows/{instanceId}/pause
POST /api/workflows/{instanceId}/resume
POST /api/workflows/{instanceId}/cancel
```

**Pause:**
- Sets `status = PAUSED`
- `AsyncStepExecutor.executeStep()` checks for `PAUSED` status at the start and exits immediately without executing
- Writes `PAUSED` event to audit log

**Resume:**
- Sets `status = RUNNING`
- Re-submits current step to `AsyncStepExecutor.executeStep()`
- Writes `RESUMED` event to audit log

**Cancel:**
- Sets `status = CANCELLED`
- No further steps execute
- Writes `CANCELLED` event to audit log
- Idempotent — calling cancel on an already-cancelled instance is a no-op

##### Guard Logic in `executeStep()`

```java
// Add at the top of executeStep(), after loading the instance:
if (instance.getStatus() == WorkflowStatus.PAUSED
    || instance.getStatus() == WorkflowStatus.CANCELLED) {
    log.info("Instance {} is {}. Skipping step execution.", instanceId, instance.getStatus());
    return CompletableFuture.completedFuture(null);
}
```

##### Engine Changes Required

| Component | Change |
|-----------|--------|
| `WorkflowStatus` | Add `PAUSED` and `CANCELLED` values |
| `AsyncStepExecutor.executeStep()` | Add status guard at entry |
| New: `WorkflowLifecycleController` | `POST /{id}/pause`, `POST /{id}/resume`, `POST /{id}/cancel` endpoints |
| New: `WorkflowLifecycleService` | Business logic for each transition with validation |
| `WorkflowExecutionLog` | Add `PAUSED`, `RESUMED`, `CANCELLED` to log action vocabulary |
