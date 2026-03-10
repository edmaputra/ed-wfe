package com.ed.workflow.controller;

import com.ed.workflow.model.WorkflowDefinition;
import com.ed.workflow.model.WorkflowInstance;
import com.ed.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows")
@lombok.RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @Deprecated(forRemoval = false)
    @PostMapping("/definitions")
    public ResponseEntity<DefinitionResponse> createDefinition(@Valid @RequestBody CreateDefinitionRequest request) {
        return createDefinitionV2(request);
    }

    public static class CreateDefinitionRequest {
        @NotBlank(message = "name is required")
        private String name;

        @NotBlank(message = "schemaJson is required")
        private String schemaJson;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSchemaJson() {
            return schemaJson;
        }

        public void setSchemaJson(String schemaJson) {
            this.schemaJson = schemaJson;
        }
    }

    @PostMapping("/definitions/v2")
    public ResponseEntity<DefinitionResponse> createDefinitionV2(@Valid @RequestBody CreateDefinitionRequest request) {
        WorkflowDefinition definition = workflowService.createDefinition(request.getName(), request.getSchemaJson());
        return ResponseEntity.ok(toDefinitionResponse(definition));
    }

    @PostMapping("/start")
    public ResponseEntity<InstanceResponse> startWorkflow(@Valid @RequestBody StartWorkflowRequest request) {
        WorkflowInstance instance = workflowService.startWorkflow(request.getName(), request.getContext());
        return ResponseEntity.ok(toInstanceResponse(instance));
    }

    @PostMapping("/{instanceId}/human-actions")
    public ResponseEntity<InstanceResponse> completeHumanTask(
            @PathVariable UUID instanceId,
            @Valid @RequestBody HumanTaskActionRequest request) {
        WorkflowInstance instance = workflowService.completeHumanTask(instanceId, request.getAction(),
                request.getContextUpdates());
        return ResponseEntity.ok(toInstanceResponse(instance));
    }

    private DefinitionResponse toDefinitionResponse(WorkflowDefinition definition) {
        return new DefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getVersion(),
                definition.getCreatedAt());
    }

    private InstanceResponse toInstanceResponse(WorkflowInstance instance) {
        return new InstanceResponse(
                instance.getId(),
                instance.getWorkflowDefinition().getName(),
                instance.getWorkflowDefinition().getVersion(),
                instance.getStatus(),
                instance.getCurrentStep(),
                instance.getCreatedAt(),
                instance.getUpdatedAt());
    }

    public static class StartWorkflowRequest {
        @NotBlank(message = "name is required")
        private String name;

        @NotNull(message = "context is required")
        private Map<String, Object> context;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public void setContext(Map<String, Object> context) {
            this.context = context;
        }
    }

    public static class HumanTaskActionRequest {
        private String action;

        private Map<String, Object> contextUpdates = new HashMap<>();

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Map<String, Object> getContextUpdates() {
            return contextUpdates;
        }

        public void setContextUpdates(Map<String, Object> contextUpdates) {
            this.contextUpdates = contextUpdates;
        }
    }

            public record DefinitionResponse(
                UUID id,
                String name,
                Integer version,
                java.time.LocalDateTime createdAt) {
            }

            public record InstanceResponse(
                UUID id,
                String workflowName,
                Integer workflowVersion,
                com.ed.workflow.model.WorkflowStatus status,
                String currentStep,
                java.time.LocalDateTime createdAt,
                java.time.LocalDateTime updatedAt) {
            }
}
