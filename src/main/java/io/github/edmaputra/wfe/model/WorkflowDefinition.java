package io.github.edmaputra.wfe.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "workflow_definitions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_workflow_definitions_key_version",
        columnNames = {"workflow_key", "version"}
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Workflow Definition entity")
public class WorkflowDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Column(nullable = false, length = 100)
    @Schema(description = "Workflow key", example = "approval_workflow")
    private String workflowKey;

    @Column(nullable = false)
    @Schema(description = "Workflow version", example = "1")
    private Integer version;

    @Column(nullable = false, length = 200)
    @Schema(description = "Workflow name", example = "Approval Workflow v1")
    private String name;

    @Column(nullable = false, columnDefinition = "JSONB")
    @Schema(description = "Workflow definition in JSON format")
    private String definitionJson;

    @Column(nullable = false)
    @Schema(description = "Active state", example = "true")
    private Boolean active;

    @Column(nullable = false, updatable = false)
    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
