package com.ed.workflow.service;

import com.ed.workflow.model.WorkflowDefinition;
import com.ed.workflow.model.WorkflowExecutionLog;
import com.ed.workflow.model.WorkflowInstance;
import com.ed.workflow.model.WorkflowStatus;
import com.ed.workflow.model.schema.WorkflowSchema;
import com.ed.workflow.model.schema.WorkflowStep;
import com.ed.workflow.repository.WorkflowExecutionLogRepository;
import com.ed.workflow.repository.WorkflowInstanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AsyncStepExecutor {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowExecutionLogRepository logRepository;
    private final JsonMapper objectMapper;
    private final org.springframework.expression.ExpressionParser parser = new org.springframework.expression.spel.standard.SpelExpressionParser();

    @Lazy
    @Autowired
    private AsyncStepExecutor self;

    @Autowired
    private IdempotencyService idempotencyService;

    // Self-injection or circular dependency might be an issue if we call back to
    // WorkflowService.
    // Ideally, this executor handles the step logic self-contained or calls
    // specific task handlers.

    @Async
    @Transactional
    public CompletableFuture<Void> executeStep(UUID instanceId, String stepId) {

        Optional<WorkflowInstance> instanceOpt = instanceRepository.findById(instanceId);
        if (instanceOpt.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        WorkflowInstance instance = instanceOpt.get();
        // Reload definition if needed, but it's eager fetched.

        try {
            WorkflowDefinition definition = instance.getWorkflowDefinition();
            WorkflowSchema schema = objectMapper.readValue(definition.getSchemaJson(), WorkflowSchema.class);

            // Validation: Ensure current step matches what we expect or just use the passed
            // stepId
            // In a real system, we might want to check if the instance is currently at this
            // step.
            if (!stepId.equals(instance.getCurrentStep())) {
                log.warn("Instance {} is at step {}, but requested to execute {}. Skipping.",
                        instanceId, instance.getCurrentStep(), stepId);
                return CompletableFuture.completedFuture(null);
            }

            Optional<WorkflowStep> stepOptional = schema.getSteps().stream()
                    .filter(s -> s.getId().equals(stepId))
                    .findFirst();

            if (stepOptional.isEmpty()) {
                throw new RuntimeException("Step not found: " + stepId);
            }

            WorkflowStep step = stepOptional.get();

            if (idempotencyService.hasStepAlreadyExecuted(instanceId, stepId) && !"decision".equals(step.getType())) {
                log.info("Step {} already executed. Skipping.", stepId);
                moveNext(instance, step.getNext());
                return CompletableFuture.completedFuture(null);
            }

            logExecution(instance, step.getId(), "STARTED", "Executing step of type: " + step.getType());

            switch (step.getType()) {
                case "service":
                    executeServiceTask(instance, step);
                    break;
                case "decision":
                    executeDecisionTask(instance, step);
                    break;

                case "human":
                    log.info("Waiting for human interaction at step: {}", step.getId());
                    logExecution(instance, step.getId(), "WAITING", "Waiting for human action");
                    // Do nothing, just return. External API must trigger next.
                    break;
                case "end":
                    completeWorkflow(instance);
                    break;
                default:
                    log.warn("Unknown step type: {}", step.getType());
                    logExecution(instance, step.getId(), "UNKNOWN", "Unknown step type: " + step.getType());
            }

        } catch (Exception e) {
            handleStepFailure(instance, stepId, e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public void completeHumanStep(UUID instanceId, String action) {
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance not found: " + instanceId));

        if (instance.getCurrentStep() == null) {
            throw new IllegalStateException("Workflow has no active step to complete.");
        }

        WorkflowDefinition definition = instance.getWorkflowDefinition();
        WorkflowSchema schema = objectMapper.readValue(definition.getSchemaJson(), WorkflowSchema.class);

        WorkflowStep currentStep = schema.getSteps().stream()
                .filter(s -> s.getId().equals(instance.getCurrentStep()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Current step not found in schema: " + instance.getCurrentStep()));

        if (!"human".equalsIgnoreCase(currentStep.getType())) {
            throw new IllegalStateException("Current step is not a human step: " + currentStep.getId());
        }

        String normalizedAction = (action == null || action.isBlank()) ? "complete" : action.trim().toLowerCase();
        String nextStepId = resolveHumanNextStep(currentStep, normalizedAction);

        logExecution(instance, currentStep.getId(), "HUMAN_" + normalizedAction.toUpperCase(),
                "Human step action received: " + normalizedAction);

        moveNext(instance, nextStepId);
    }

    private String resolveHumanNextStep(WorkflowStep step, String action) {
        if (step.getParams() != null && step.getParams().get("actions") instanceof java.util.Map<?, ?> actions) {
            Object mappedTarget = actions.get(action);
            if (mappedTarget instanceof String mappedNext && !mappedNext.isBlank()) {
                return mappedNext;
            }
        }

        return step.getNext();
    }

    private void handleStepFailure(WorkflowInstance instance, String stepId, Exception e) {
        log.error("Error executing step {} for instance {}", stepId, instance.getId(), e);

        try {
            // Need to reload instance to get latest retry count if not passed directly, but
            // instance passed is likely stale if we saved within try?
            // Actually transaction might be rolled back? No, we are catching exception.

            // Check max retries from step definition? We assume default 3 for now or need
            // to look up step def again.
            // Since we don't have step object here easily without reloading schema, let's
            // reload schema or pass step.
            // But we can't easily pass step if exception happened before step was resolved.
            // Let's reload definition.
            WorkflowDefinition definition = instance.getWorkflowDefinition();
            WorkflowSchema schema = objectMapper.readValue(definition.getSchemaJson(), WorkflowSchema.class);
            Optional<WorkflowStep> stepOpt = schema.getSteps().stream().filter(s -> s.getId().equals(stepId))
                    .findFirst();

            int maxRetries = 3;
            if (stepOpt.isPresent() && stepOpt.get().getParams() != null
                    && stepOpt.get().getParams().containsKey("retries")) {
                maxRetries = (Integer) stepOpt.get().getParams().get("retries");
            }

            if (instance.getRetryCount() < maxRetries) {
                int retryCount = instance.getRetryCount() + 1;
                instance.setRetryCount(retryCount);
                instanceRepository.save(instance); // Update count

                long delay = (long) (1000 * Math.pow(2, retryCount - 1)); // Exponential backoff: 1s, 2s, 4s...

                log.info("Scheduling retry #{} for step {} in {}ms", retryCount, stepId, delay);
                logExecution(instance, stepId, "RETRY_SCHEDULED",
                        "Scheduling retry #" + retryCount + " in " + delay + "ms. Error: " + e.getMessage());

                CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
                    if (self != null) {
                        self.executeStep(instance.getId(), stepId);
                    }
                });

            } else {
                log.error("Max retries reached for steps {}", stepId);
                logExecution(instance, stepId, "FAILED", "Max retries reached. Error: " + e.getMessage());
                instance.setStatus(WorkflowStatus.FAILED);
                instanceRepository.save(instance);
            }
        } catch (Exception ex) {
            log.error("Error handling failure", ex);
            // Fallback fail
            instance.setStatus(WorkflowStatus.FAILED);
            instanceRepository.save(instance);
        }
    }

    private void executeServiceTask(WorkflowInstance instance, WorkflowStep step) {
        try {
            // Simulate work
            Thread.sleep(1000);
            log.info("Service task {} completed", step.getId());
            logExecution(instance, step.getId(), "COMPLETED", "Service task execution successful");

            moveNext(instance, step.getNext());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during execution", e);
        }
    }

    private void moveNext(WorkflowInstance instance, String nextStepId) {
        if (nextStepId == null) {
            completeWorkflow(instance);
            return;
        }

        instance.setCurrentStep(nextStepId);
        instanceRepository.save(instance);

        // Trigger next step
        // We can call executeStep again. Since it's @Async, it will submit to executor.
        // But we need to be careful about Transaction boundaries.
        // This method is running inside a transaction. The next call will strictly be a
        // new transaction if it's a new async call.
        // However, we can't call this.executeStep() because of proxy issues (calling
        // async on self).
        // So we need to either inject self or use an event or just return and let a
        // listener handle it.
        // For simplicity, let's inject a context or use a helper.
        // Actually, we can return the next step ID and have the caller handle it? No,
        // it's async void.

        // I'll use a hack for now:
        // context.getBean(AsyncStepExecutor.class).executeStep(...)
        // Or better, just wire the bean.
        // But circular dependency: Executor -> Executor.
        // Using @Lazy or Provider.

        executeNextStep(instance.getId(), nextStepId);
    }

    // We will inject this via setter or provider to avoid cycle in constructor if
    // needed,
    // or just rely on the fact that spring handles singleton self-injection
    // sometimes if done right (e.g. @Autowired private AsyncStepExecutor self).

    private void executeNextStep(UUID instanceId, String nextStepId) {
        if (self != null) {
            self.executeStep(instanceId, nextStepId);
        } else {
            log.error("Self reference is null, cannot execute next step async");
        }
    }

    private void completeWorkflow(WorkflowInstance instance) {
        instance.setStatus(WorkflowStatus.COMPLETED);
        instance.setCurrentStep(null);
        instanceRepository.save(instance);
        logExecution(instance, "END", "COMPLETED", "Workflow completed successfully");
    }

    private void executeDecisionTask(WorkflowInstance instance, WorkflowStep step) {
        try {
            log.info("Evaluating decision for step: {}", step.getId());
            logExecution(instance, step.getId(), "EVALUATING", "Evaluating decision rules");

            String nextStepId = null;
            java.util.List<java.util.Map<String, Object>> rules = (java.util.List<java.util.Map<String, Object>>) step
                    .getParams().get("rules");

            if (rules != null) {
                // Parse context to Map for SpEL
                java.util.Map<String, Object> contextMap = objectMapper.readValue(instance.getContext(),
                        java.util.Map.class);
                org.springframework.expression.EvaluationContext context = new org.springframework.expression.spel.support.StandardEvaluationContext();
                context.setVariable("context", contextMap); // Access as #context.field

                for (java.util.Map<String, Object> rule : rules) {
                    String condition = (String) rule.get("condition");
                    String target = (String) rule.get("next");

                    if ("default".equalsIgnoreCase(condition) || "true".equalsIgnoreCase(condition)) {
                        nextStepId = target;
                    } else {
                        try {
                            Boolean result = parser.parseExpression(condition).getValue(context, Boolean.class);
                            if (Boolean.TRUE.equals(result)) {
                                nextStepId = target;
                                break;
                            }
                        } catch (Exception e) {
                            log.error("Error evaluating condition: {}", condition, e);
                        }
                    }
                }
            }

            if (nextStepId == null) {
                nextStepId = step.getNext();
            }

            logExecution(instance, step.getId(), "COMPLETED", "Decision result: " + nextStepId);
            moveNext(instance, nextStepId);

        } catch (Exception e) {
            log.error("Error in decision task", e);
            throw new RuntimeException("Decision failed", e);
        }
    }

    private void logExecution(WorkflowInstance instance, String stepId, String action, String message) {
        WorkflowExecutionLog log = new WorkflowExecutionLog(instance, stepId, action, message);
        logRepository.save(log);
    }
}
