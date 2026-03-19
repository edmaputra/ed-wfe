# Workflow Engine Build Plan

## 1. Project Goals

Build a reliable Workflow Engine that can:
- Create and version workflow definitions.
- Start workflow instances from a selected definition version.
- Execute steps asynchronously with retries and timeouts.
- Support service, human, decision, and end step types.
- Track execution logs for full auditability.
- Expose stable REST APIs for workflow operations.

Success criteria:
- Workflows run from start to completion with predictable status transitions.
- Human and decision steps behave correctly with context-driven routing.
- Retry and timeout policies prevent stuck or duplicate execution.
- Integration tests verify critical user paths.

## 2. Scope (MVP)

Include:
- Workflow definition CRUD with name + version uniqueness.
- Workflow start and instance status tracking.
- Async step execution orchestration.
- Human task completion endpoint.
- Decision step routing with expression evaluation.
- Timeout monitoring and failure handling.
- Execution logs for each status/step transition.
- Idempotency checks per step execution.

Exclude for MVP (future phase):
- UI dashboard.
- Multi-tenant isolation.
- Event bus integration.
- Distributed scheduler beyond a single service instance.

## 3. High-Level Architecture

Layers:
- Controller layer: REST endpoints, validation, API responses.
- Service layer: orchestration, execution engine, timeout monitor, idempotency.
- Repository layer: persistence operations.
- Model layer: workflow definition, instance, execution log, status enums.
- Schema layer: in-memory workflow schema and step configuration.

Core runtime flow:
1. Client creates workflow definition (versioned schema).
2. Client starts a workflow instance from a definition version.
3. Engine transitions instance to RUNNING and executes steps asynchronously.
4. Each step writes output into workflow context.
5. Decision/human/end steps alter routing and lifecycle.
6. Logs are written for every key transition.
7. Workflow ends as COMPLETED or FAILED.

## 4. Data Model Design

Entities:
- WorkflowDefinition
  - id, name, version, schemaJson, active flag, timestamps.
  - unique constraint on (name, version).
- WorkflowInstance
  - id, definitionId, status, currentStep, contextJson, startedAt, endedAt, error.
- WorkflowExecutionLog
  - id, instanceId, stepName, eventType, message, attempt, createdAt.

Enums:
- WorkflowStatus: PENDING, RUNNING, COMPLETED, FAILED.
- Optional log event enum: STARTED, STEP_STARTED, STEP_COMPLETED, RETRY, TIMEOUT, FAILED, COMPLETED.

## 5. API Design

Version strategy:
- Primary path: /api/v2/workflows.
- Keep older versions only if required for compatibility.

Endpoints (MVP):
- POST /definitions
- GET /definitions
- GET /definitions/{id}
- POST /definitions/{id}/start
- GET /instances/{instanceId}
- GET /instances/{instanceId}/logs
- POST /instances/{instanceId}/human-tasks/{stepName}/complete

API behavior requirements:
- Validate schema before saving definition.
- Return clear, structured error responses.
- Enforce state-machine constraints for transitions.

## 6. Execution Engine Design

Step types and behavior:
- service: run automated task handler.
- human: pause execution and wait for completion API.
- decision: evaluate expression against context and choose next step.
- end: stop and mark workflow complete.

Execution rules:
- Use async execution for non-blocking processing.
- Perform idempotency check before processing a step.
- Apply retry policy with exponential backoff:
  - delay = baseDelayMs * 2^attempt
- Record logs before and after step execution.
- Update instance status atomically to avoid race conditions.

Error handling:
- Retriable failure: schedule retry and keep RUNNING.
- Non-retriable failure or retries exhausted: mark FAILED.
- Timeout monitor marks stalled steps as FAILED and logs event.

## 7. Validation and Security Baseline

Validation:
- Schema structural validation (required fields, step ordering, step type rules).
- Referential validation (next-step exists, no invalid decision routes).
- API input validation via request constraints.

Security baseline:
- Add authentication placeholder and role model for later integration.
- Protect mutating endpoints.
- Audit actor identity in execution logs where available.

## 8. Testing Strategy

Test layers:
- Unit tests:
  - step routing logic,
  - expression evaluation,
  - retry/backoff math,
  - timeout decision logic,
  - idempotency checks.
- Integration tests:
  - create definition -> start workflow -> complete execution,
  - human task pause/resume path,
  - decision branch selection,
  - retry then success,
  - retry exhaustion -> FAILED,
  - timeout -> FAILED.

Quality gates:
- All tests pass in CI.
- Critical-path integration tests run on every pull request.

## 9. Delivery Plan (Phased)

Phase 0: Bootstrap
- Initialize project skeleton and dependencies.
- Configure application profiles and database connectivity.

Phase 1: Core Domain + Persistence
- Implement entities, repositories, and status model.
- Add schema serialization/deserialization.

Phase 2: Definition APIs
- Implement definition CRUD and schema validation.
- Add versioning constraints and tests.

Phase 3: Runtime Execution
- Implement workflow start and async step executor.
- Add service/human/decision/end step handling.

Phase 4: Reliability Features
- Implement idempotency service and retry/backoff.
- Implement timeout monitor and failure transitions.

Phase 5: Observability + Audit
- Add execution logs and query APIs.
- Improve error model and diagnostics.

Phase 6: Hardening
- Add comprehensive integration tests.
- Tune performance and concurrency behavior.
- Prepare release checklist and runbook.

## 10. Acceptance Checklist

Functional:
- Definition versioning works with uniqueness constraints.
- Instance lifecycle transitions are valid and consistent.
- Human tasks pause and resume correctly.
- Decision steps route correctly by context.
- Timeout and retry logic operate as expected.

Non-functional:
- Logs provide complete traceability.
- API errors are consistent and actionable.
- Integration test suite passes reliably.

Operational:
- Local development setup is documented.
- Build, test, and run commands are verified.
- Monitoring and troubleshooting notes exist.

## 11. Risks and Mitigations

Risk: duplicate execution due to async retries.
Mitigation: strict idempotency checks and transactional boundaries.

Risk: invalid decision expressions at runtime.
Mitigation: schema-time expression validation plus guarded runtime evaluation.

Risk: race conditions in instance updates.
Mitigation: optimistic locking and carefully scoped transactions.

Risk: long-running workflows becoming hard to debug.
Mitigation: rich execution logs and stable correlation IDs.

## 12. Immediate Next Steps

1. Recreate project skeleton (Gradle, Spring Boot, package layout).
2. Implement core entities and repositories.
3. Build definition APIs with validation.
4. Add runtime executor and human task completion flow.
5. Add retries, idempotency, timeout monitoring, and integration tests.
