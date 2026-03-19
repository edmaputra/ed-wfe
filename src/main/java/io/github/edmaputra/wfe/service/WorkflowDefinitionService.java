package io.github.edmaputra.wfe.service;

import io.github.edmaputra.wfe.dto.CreateWorkflowDefinitionRequest;
import io.github.edmaputra.wfe.dto.WorkflowDefinitionResponse;
import io.github.edmaputra.wfe.model.WorkflowDefinition;
import io.github.edmaputra.wfe.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    /**
     * Create a new workflow definition.
     *
     * @param request the create request containing workflow definition details
     * @return the created workflow definition response
     * @throws IllegalArgumentException if a definition with the same key and version already exists
     */
    @Transactional
    public WorkflowDefinitionResponse createWorkflowDefinition(CreateWorkflowDefinitionRequest request) {
        // Check if the definition already exists
        if (workflowDefinitionRepository.existsByWorkflowKeyAndVersion(request.getWorkflowKey(), request.getVersion())) {
            throw new IllegalArgumentException(
                String.format("Workflow definition already exists for key '%s' version %d",
                    request.getWorkflowKey(), request.getVersion())
            );
        }

        WorkflowDefinition definition = WorkflowDefinition.builder()
            .workflowKey(request.getWorkflowKey())
            .version(request.getVersion())
            .name(request.getName())
            .definitionJson(request.getDefinitionJson())
            .active(request.getActive() != null ? request.getActive() : true)
            .build();

        WorkflowDefinition savedDefinition = workflowDefinitionRepository.save(definition);

        return mapToResponse(savedDefinition);
    }

    /**
     * Map a WorkflowDefinition entity to a response DTO.
     *
     * @param definition the workflow definition entity
     * @return the workflow definition response
     */
    private WorkflowDefinitionResponse mapToResponse(WorkflowDefinition definition) {
        return WorkflowDefinitionResponse.builder()
            .id(definition.getId())
            .workflowKey(definition.getWorkflowKey())
            .version(definition.getVersion())
            .name(definition.getName())
            .definitionJson(definition.getDefinitionJson())
            .active(definition.getActive())
            .createdAt(definition.getCreatedAt())
            .updatedAt(definition.getUpdatedAt())
            .build();
    }
}
