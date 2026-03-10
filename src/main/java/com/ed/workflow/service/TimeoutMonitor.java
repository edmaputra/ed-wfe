package com.ed.workflow.service;

import com.ed.workflow.model.WorkflowDefinition;
import com.ed.workflow.model.WorkflowExecutionLog;
import com.ed.workflow.model.WorkflowInstance;
import com.ed.workflow.model.WorkflowStatus;
import com.ed.workflow.model.schema.WorkflowSchema;
import com.ed.workflow.model.schema.WorkflowStep;
import com.ed.workflow.repository.WorkflowExecutionLogRepository;
import com.ed.workflow.repository.WorkflowInstanceRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class TimeoutMonitor {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowExecutionLogRepository logRepository;
    private final JsonMapper objectMapper;

    @Scheduled(fixedRate = 10000) // Check every 10 seconds
    @Transactional
    public void checkTimeouts() {
        log.debug("Checking for timed-out workflows...");

        List<WorkflowInstance> runningInstances = instanceRepository.findByStatus(WorkflowStatus.RUNNING);

        for (WorkflowInstance instance : runningInstances) {
            try {
                checkInstanceTimeout(instance);
            } catch (Exception e) {
                log.error("Error checking timeout for instance {}", instance.getId(), e);
            }
        }
    }

    private void checkInstanceTimeout(WorkflowInstance instance) {
        try {
            WorkflowDefinition definition = instance.getWorkflowDefinition();
            WorkflowSchema schema = objectMapper.readValue(definition.getSchemaJson(), WorkflowSchema.class);
            String currentStepId = instance.getCurrentStep();

            if (currentStepId == null)
                return;

            Optional<WorkflowStep> stepOpt = schema.getSteps().stream()
                    .filter(s -> s.getId().equals(currentStepId))
                    .findFirst();

            if (stepOpt.isPresent()) {
                WorkflowStep step = stepOpt.get();
                Long timeout = step.getTimeout();

                if (timeout != null && timeout > 0) {
                    LocalDateTime updatedAt = instance.getUpdatedAt();
                    if (updatedAt.plusNanos(timeout * 1000000).isBefore(LocalDateTime.now())) {
                        log.warn("Step {} in instance {} timed out. Timeout: {}ms", step.getId(), instance.getId(),
                                timeout);

                        logExecution(instance, step.getId(), "TIMEOUT", "Step timed out after " + timeout + "ms");

                        instance.setStatus(WorkflowStatus.FAILED);
                        instanceRepository.save(instance);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse schema for instance {}", instance.getId(), e);
        }
    }

    private void logExecution(WorkflowInstance instance, String stepId, String action, String message) {
        WorkflowExecutionLog log = new WorkflowExecutionLog(instance, stepId, action, message);
        logRepository.save(log);
    }
}
