package io.github.edmaputra.wfe.controller;

import io.github.edmaputra.wfe.dto.CreateWorkflowDefinitionRequest;
import io.github.edmaputra.wfe.dto.WorkflowDefinitionResponse;
import io.github.edmaputra.wfe.service.WorkflowDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/definitions")
@RequiredArgsConstructor
@Tag(name = "Workflow Definitions", description = "Manage workflow definitions")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;

    @PostMapping
    @Operation(
        summary = "Create a new workflow definition",
        description = "Creates a new workflow definition with the provided schema and configuration"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Workflow definition created successfully",
            content = @Content(schema = @Schema(implementation = WorkflowDefinitionResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request body or duplicate workflow key/version"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Workflow definition with the same key and version already exists"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public ResponseEntity<WorkflowDefinitionResponse> createWorkflowDefinition(
        @Valid @RequestBody CreateWorkflowDefinitionRequest request) {
        
        try {
            WorkflowDefinitionResponse response = workflowDefinitionService.createWorkflowDefinition(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
