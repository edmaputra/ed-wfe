package com.ed.workflow.repository;

import com.ed.workflow.model.WorkflowDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {
    Optional<WorkflowDefinition> findByNameAndVersion(String name, Integer version);

    List<WorkflowDefinition> findByName(String name);

}
