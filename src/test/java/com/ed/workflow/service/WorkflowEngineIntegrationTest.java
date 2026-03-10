package com.ed.workflow.service;

import com.ed.workflow.controller.WorkflowController;
import com.ed.workflow.model.WorkflowDefinition;
import com.ed.workflow.model.WorkflowExecutionLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class WorkflowEngineIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        // Clear child tables first to avoid FK constraints
//        jdbcTemplate.execute("TRUNCATE workflow_execution_log");
//        jdbcTemplate.execute("TRUNCATE workflow_instance");
//        jdbcTemplate.execute("TRUNCATE workflow_definition");

        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
    }

    private String createURLWithPort(String uri) {
        return "http://localhost:" + port + uri;
    }

    @Test
    void testCreateWorkflowDefinition() {
        String workflowName = "test-workflow";
        String schemaJson = "{\"steps\": []}";

        Map<String, String> request = Map.of(
                "name", workflowName,
                "schemaJson", schemaJson);

        ResponseEntity<WorkflowController.DefinitionResponse> response = restTemplate.postForEntity(
                createURLWithPort("/api/workflows/definitions/v2"),
                request,
                WorkflowController.DefinitionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo(workflowName);
    }

    @Test
    void testStartWorkflow() {
        // 1. Create Definition
        String workflowName = "execution-test";
        String schemaJson = "{\"steps\": []}";
        Map<String, String> defRequest = Map.of("name", workflowName, "schemaJson", schemaJson);
        restTemplate.postForEntity(createURLWithPort("/api/workflows/definitions/v2"), defRequest,
                WorkflowDefinition.class);

        // 2. Start Workflow
        Map<String, Object> startRequest = Map.of(
                "name", workflowName,
                "context", Map.of("key", "value"));

        ResponseEntity<WorkflowController.InstanceResponse> response = restTemplate.postForEntity(
                createURLWithPort("/api/workflows/start"),
                startRequest,
                WorkflowController.InstanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().status()).isNotNull();
    }

    @Test
    void testGetAuditLogs() {
        // 1. Create Definition
        String workflowName = "audit-test";
        restTemplate.postForEntity(
                createURLWithPort("/api/workflows/definitions/v2"),
                Map.of("name", workflowName, "schemaJson", "{}"),
                WorkflowDefinition.class);

        // 2. Start Workflow
        ResponseEntity<WorkflowController.InstanceResponse> startResponse = restTemplate.postForEntity(
                createURLWithPort("/api/workflows/start"),
                Map.of("name", workflowName, "context", Map.of()),
                WorkflowController.InstanceResponse.class);
        UUID instanceId = startResponse.getBody().id();

        // 3. Get Audit Logs
        // Endpoint: /api/workflows/logs/{instanceId}
        ResponseEntity<WorkflowExecutionLog[]> auditResponse = restTemplate.getForEntity(
                createURLWithPort("/api/workflows/logs/" + instanceId),
                WorkflowExecutionLog[].class);

        assertThat(auditResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // It might be empty if no steps ran, but should be 200 OK
        assertThat(auditResponse.getBody()).isNotNull();
    }

    @Test
    void testCompleteHumanTask() {
        String workflowName = "human-action-test";
        String schemaJson = """
                {
                  "startStep": "review",
                  "steps": [
                    {
                      "id": "review",
                      "type": "human",
                      "next": "end"
                    },
                    {
                      "id": "end",
                      "type": "end"
                    }
                  ]
                }
                """;

        restTemplate.postForEntity(
                createURLWithPort("/api/workflows/definitions/v2"),
                Map.of("name", workflowName, "schemaJson", schemaJson),
                WorkflowDefinition.class);

        ResponseEntity<WorkflowController.InstanceResponse> startResponse = restTemplate.postForEntity(
                createURLWithPort("/api/workflows/start"),
                Map.of("name", workflowName, "context", Map.of("approved", false)),
                WorkflowController.InstanceResponse.class);

        UUID instanceId = startResponse.getBody().id();

        ResponseEntity<WorkflowController.InstanceResponse> actionResponse = restTemplate.postForEntity(
                createURLWithPort("/api/workflows/" + instanceId + "/human-actions"),
                Map.of("action", "complete", "contextUpdates", Map.of("approved", true)),
                WorkflowController.InstanceResponse.class);

        assertThat(actionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actionResponse.getBody()).isNotNull();

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ResponseEntity<WorkflowExecutionLog[]> logsResponse = restTemplate.getForEntity(
                createURLWithPort("/api/workflows/logs/" + instanceId),
                WorkflowExecutionLog[].class);

        assertThat(logsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logsResponse.getBody()).isNotNull();

        List<String> actions = java.util.Arrays.stream(logsResponse.getBody())
                .map(WorkflowExecutionLog::getAction)
                .toList();

        assertThat(actions).contains("WAITING", "HUMAN_COMPLETE");
    }

        @Test
        void testCreateDefinitionValidationError() {
                ResponseEntity<String> response = restTemplate.postForEntity(
                                createURLWithPort("/api/workflows/definitions/v2"),
                                Map.of("schemaJson", "{}"),
                                String.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody()).contains("Validation failed");
        }
}
