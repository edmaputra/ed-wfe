package io.github.edmaputra.wfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.edmaputra.wfe.dto.CreateWorkflowDefinitionRequest;
import io.github.edmaputra.wfe.dto.WorkflowDefinitionResponse;
import io.github.edmaputra.wfe.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("Workflow Definition Controller Integration Tests")
class WorkflowDefinitionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;

    private CreateWorkflowDefinitionRequest createValidRequest() {
        return CreateWorkflowDefinitionRequest.builder()
            .workflowKey("test_workflow")
            .version(1)
            .name("Test Workflow")
            .definitionJson("{\"steps\": []}")
            .active(true)
            .build();
    }

    @BeforeEach
    void setUp() {
        workflowDefinitionRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully create a workflow definition")
    void testCreateWorkflowDefinition_Success() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = createValidRequest();

        // Act
        MvcResult result = mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.workflowKey").value("test_workflow"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.name").value("Test Workflow"))
            .andExpect(jsonPath("$.definitionJson").value("{\"steps\": []}"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn();

        // Assert
        String responseContent = result.getResponse().getContentAsString();
        WorkflowDefinitionResponse response = objectMapper.readValue(responseContent, WorkflowDefinitionResponse.class);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getWorkflowKey()).isEqualTo("test_workflow");
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getName()).isEqualTo("Test Workflow");
        assertThat(response.getActive()).isTrue();

        // Verify it was saved to database
        assertThat(workflowDefinitionRepository.findById(response.getId())).isPresent();
    }

    @Test
    @DisplayName("Should return 409 when creating duplicate workflow definition")
    void testCreateWorkflowDefinition_DuplicateKeyVersion() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = createValidRequest();

        // Create first definition
        mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Act & Assert - Try to create duplicate
        mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should return 400 when workflowKey is blank")
    void testCreateWorkflowDefinition_MissingWorkflowKey() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("")
            .version(1)
            .name("Test Workflow")
            .definitionJson("{\"steps\": []}")
            .active(true)
            .build();

        // Act & Assert
        mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when version is null")
    void testCreateWorkflowDefinition_NullVersion() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("test_workflow")
            .version(null)
            .name("Test Workflow")
            .definitionJson("{\"steps\": []}")
            .active(true)
            .build();

        // Act & Assert
        mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when version is not positive")
    void testCreateWorkflowDefinition_NegativeVersion() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("test_workflow")
            .version(-1)
            .name("Test Workflow")
            .definitionJson("{\"steps\": []}")
            .active(true)
            .build();

        // Act & Assert
        mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when name is blank")
    void testCreateWorkflowDefinition_MissingName() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("test_workflow")
            .version(1)
            .name("")
            .definitionJson("{\"steps\": []}")
            .active(true)
            .build();

        // Act & Assert
        mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when definitionJson is blank")
    void testCreateWorkflowDefinition_MissingDefinitionJson() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("test_workflow")
            .version(1)
            .name("Test Workflow")
            .definitionJson("")
            .active(true)
            .build();

        // Act & Assert
        mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should successfully create workflow definition with default active state")
    void testCreateWorkflowDefinition_DefaultActiveState() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("test_workflow_2")
            .version(1)
            .name("Test Workflow 2")
            .definitionJson("{\"steps\": []}")
            .active(null)  // Explicitly set to null to test default
            .build();

        // Act
        MvcResult result = mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.active").value(true))
            .andReturn();

        // Assert
        String responseContent = result.getResponse().getContentAsString();
        WorkflowDefinitionResponse response = objectMapper.readValue(responseContent, WorkflowDefinitionResponse.class);

        assertThat(response.getActive()).isTrue();
    }

    @Test
    @DisplayName("Should successfully create workflow definition with different versions of same key")
    void testCreateWorkflowDefinition_MultipleVersions() throws Exception {
        // Arrange
        CreateWorkflowDefinitionRequest request1 = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("multi_version_workflow")
            .version(1)
            .name("Multi Version Workflow v1")
            .definitionJson("{\"steps\": []}")
            .active(true)
            .build();

        CreateWorkflowDefinitionRequest request2 = CreateWorkflowDefinitionRequest.builder()
            .workflowKey("multi_version_workflow")
            .version(2)
            .name("Multi Version Workflow v2")
            .definitionJson("{\"steps\": [{\"name\": \"step1\"}]}")
            .active(true)
            .build();

        // Act
        MvcResult result1 = mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated())
            .andReturn();

        MvcResult result2 = mockMvc.perform(post("/definitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated())
            .andReturn();

        // Assert
        String content1 = result1.getResponse().getContentAsString();
        String content2 = result2.getResponse().getContentAsString();

        WorkflowDefinitionResponse response1 = objectMapper.readValue(content1, WorkflowDefinitionResponse.class);
        WorkflowDefinitionResponse response2 = objectMapper.readValue(content2, WorkflowDefinitionResponse.class);

        assertThat(response1.getWorkflowKey()).isEqualTo("multi_version_workflow");
        assertThat(response1.getVersion()).isEqualTo(1);
        assertThat(response2.getWorkflowKey()).isEqualTo("multi_version_workflow");
        assertThat(response2.getVersion()).isEqualTo(2);

        // Verify both are in database
        assertThat(workflowDefinitionRepository.findByWorkflowKeyOrderByVersionDesc("multi_version_workflow"))
            .hasSize(2);
    }
}
