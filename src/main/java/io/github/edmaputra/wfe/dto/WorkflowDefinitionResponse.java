package io.github.edmaputra.wfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Workflow definition response")
public class WorkflowDefinitionResponse {

    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "Workflow key", example = "approval_workflow")
    private String workflowKey;

    @Schema(description = "Workflow version", example = "1")
    private Integer version;

    @Schema(description = "Workflow name", example = "Approval Workflow v1")
    private String name;

    @Schema(description = "Workflow definition in JSON format")
    private String definitionJson;

    @Schema(description = "Active state", example = "true")
    private Boolean active;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
