package com.ed.workflow.repository;

import com.ed.workflow.model.WorkflowExecutionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowExecutionLogRepository extends JpaRepository<WorkflowExecutionLog, UUID> {
    List<WorkflowExecutionLog> findByWorkflowInstanceIdOrderByTimestampAsc(UUID workflowInstanceId);
}
