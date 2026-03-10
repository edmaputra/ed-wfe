package com.ed.workflow.controller;

import com.ed.workflow.model.WorkflowExecutionLog;
import com.ed.workflow.repository.WorkflowExecutionLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/logs")
@lombok.RequiredArgsConstructor
public class WorkflowAuditController {

    private final WorkflowExecutionLogRepository logRepository;

    @GetMapping("/{instanceId}")
    public List<WorkflowExecutionLog> getLogs(@PathVariable UUID instanceId) {
        return logRepository.findByWorkflowInstanceIdOrderByTimestampAsc(instanceId);
    }
}
