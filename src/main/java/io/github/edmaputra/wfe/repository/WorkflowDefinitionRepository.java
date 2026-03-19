package io.github.edmaputra.wfe.repository;

import io.github.edmaputra.wfe.model.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    /**
     * Find the latest version of a workflow by workflow key.
     *
     * @param workflowKey the workflow key
     * @return the latest version of the workflow definition
     */
    Optional<WorkflowDefinition> findFirstByWorkflowKeyOrderByVersionDesc(String workflowKey);

    /**
     * Find a specific version of a workflow.
     *
     * @param workflowKey the workflow key
     * @param version     the workflow version
     * @return the workflow definition at the specified version
     */
    Optional<WorkflowDefinition> findByWorkflowKeyAndVersion(String workflowKey, Integer version);

    /**
     * Find all versions of a workflow by key.
     *
     * @param workflowKey the workflow key
     * @return list of all versions for the workflow
     */
    List<WorkflowDefinition> findByWorkflowKeyOrderByVersionDesc(String workflowKey);

    /**
     * Check if a workflow with the given key and version already exists.
     *
     * @param workflowKey the workflow key
     * @param version     the workflow version
     * @return true if the workflow definition exists, false otherwise
     */
    boolean existsByWorkflowKeyAndVersion(String workflowKey, Integer version);
}
