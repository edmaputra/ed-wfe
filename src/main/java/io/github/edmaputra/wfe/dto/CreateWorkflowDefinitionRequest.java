package io.github.edmaputra.wfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new workflow definition")
public class CreateWorkflowDefinitionRequest {

    @NotBlank(message = "Workflow key cannot be blank")
    @Schema(description = "Unique workflow key", example = "approval_workflow")
    private String workflowKey;

    @NotNull(message = "Version cannot be null")
    @Positive(message = "Version must be positive")
    @Schema(description = "Workflow version", example = "1")
    private Integer version;

    @NotBlank(message = "Workflow name cannot be blank")
    @Schema(description = "Human-readable workflow name", example = "Approval Workflow v1")
    private String name;

    @NotBlank(message = "Definition cannot be blank")
    @Schema(description = "Workflow definition in JSON format")
    private String definitionJson;

    @Schema(description = "Active state (default: true)", example = "true")
    private Boolean active;
}
