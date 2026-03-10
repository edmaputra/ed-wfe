package com.ed.workflow.model.schema;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStep {
    private String id;
    private String type; // "service", "human", "decision"
    private String next; // ID of the next step
    private Map<String, Object> params;
    private Long timeout; // Timeout in milliseconds
}
