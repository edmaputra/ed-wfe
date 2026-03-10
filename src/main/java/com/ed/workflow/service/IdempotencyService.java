package com.ed.workflow.service;

import com.ed.workflow.model.WorkflowExecutionLog;
import com.ed.workflow.repository.WorkflowExecutionLogRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class IdempotencyService {

    private final WorkflowExecutionLogRepository logRepository;

    public boolean hasStepAlreadyExecuted(UUID instanceId, String stepId) {
        // Simple check: has this step COMPLETED successfully before?
        // We look at logs.
        // Optimized: DB query
        return logRepository.findByWorkflowInstanceIdOrderByTimestampAsc(instanceId).stream()
                .anyMatch(log -> log.getStepId().equals(stepId) && "COMPLETED".equals(log.getAction()));
    }
}
