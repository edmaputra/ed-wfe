package com.ed.workflow.model.schema;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowSchema {
    private String version;
    private String startStep;
    private List<WorkflowStep> steps;
}
