package com.ed.workflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "workflow_execution_logs")
public class WorkflowExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(nullable = false)
    private String stepId;

    @Column(nullable = false)
    private String action; // e.g., STARTED, COMPLETED, FAILED

    @Column(columnDefinition = "text")
    private String message;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public WorkflowExecutionLog(WorkflowInstance instance, String stepId, String action, String message) {
        this.workflowInstance = instance;
        this.stepId = stepId;
        this.action = action;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
