package com.ed.workflow.repository;

import com.ed.workflow.model.WorkflowInstance;
import com.ed.workflow.model.WorkflowStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    List<WorkflowInstance> findByStatus(WorkflowStatus status);
}
