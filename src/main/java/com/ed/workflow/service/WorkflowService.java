package com.ed.workflow.service;

import com.ed.workflow.model.WorkflowDefinition;
import com.ed.workflow.model.WorkflowInstance;
import com.ed.workflow.model.WorkflowStatus;
import com.ed.workflow.model.schema.WorkflowSchema;
import com.ed.workflow.repository.WorkflowDefinitionRepository;
import com.ed.workflow.repository.WorkflowInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class WorkflowService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final JsonMapper objectMapper;

    private final AsyncStepExecutor stepExecutor;

    @Transactional
    public WorkflowDefinition createDefinition(String name, String schemaJson) {
        // Validate JSON
        WorkflowSchema schema = objectMapper.readValue(schemaJson, WorkflowSchema.class);

        // Find latest version
        List<WorkflowDefinition> existing = definitionRepository.findByName(name);
        int newVersion = 1;
        if (!existing.isEmpty()) {
            newVersion = existing.stream()
                    .mapToInt(WorkflowDefinition::getVersion)
                    .max()
                    .orElse(0) + 1;
        }

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(name);
        definition.setSchemaJson(schemaJson);
        definition.setVersion(newVersion);

        return definitionRepository.save(definition);

    }

    @Transactional
    public WorkflowInstance startWorkflow(String name, Map<String, Object> initialContext) {
        // Find latest version
        List<WorkflowDefinition> definitions = definitionRepository.findByName(name);
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("Workflow definition not found: " + name);
        }

        WorkflowDefinition definition = definitions.stream()
                .max(java.util.Comparator.comparingInt(WorkflowDefinition::getVersion))
                .orElseThrow();

        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowDefinition(definition);
        instance.setStatus(WorkflowStatus.RUNNING);

        WorkflowSchema schema = objectMapper.readValue(definition.getSchemaJson(), WorkflowSchema.class);
        instance.setCurrentStep(schema.getStartStep());
        instance.setContext(objectMapper.writeValueAsString(initialContext));

        instance = instanceRepository.save(instance);

        // Trigger execution of the first step
        stepExecutor.executeStep(instance.getId(), instance.getCurrentStep());

        return instance;
    }

    @Transactional
    public WorkflowInstance completeHumanTask(UUID instanceId, String action, Map<String, Object> contextUpdates) {
        WorkflowInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow instance not found: " + instanceId));

        if (contextUpdates != null && !contextUpdates.isEmpty()) {
            Map<String, Object> existingContext = instance.getContext() == null || instance.getContext().isBlank()
                    ? new java.util.HashMap<>()
                    : objectMapper.readValue(instance.getContext(), java.util.Map.class);

            existingContext.putAll(contextUpdates);
            instance.setContext(objectMapper.writeValueAsString(existingContext));
            instanceRepository.save(instance);
        }

        stepExecutor.completeHumanStep(instanceId, action);

        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalStateException("Workflow instance not found after human action: " + instanceId));
    }
}
