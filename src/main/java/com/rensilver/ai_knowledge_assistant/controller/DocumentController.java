package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.document.DocumentService;
import com.rensilver.ai_knowledge_assistant.dto.DocumentResponse;
import com.rensilver.ai_knowledge_assistant.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Documents", description = "Upload, list, retrieve, and delete knowledge-base documents.")
@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Upload a document for ingestion",
            description = "Stores the file and returns immediately with status PROCESSING; parsing, "
                    + "chunking, embedding, and indexing happen asynchronously in the background. "
                    + "Poll GET /documents/{id} for status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document stored, ingestion started",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported file type",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "File exceeds the maximum upload size",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Document could not be processed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token (no response body)")
    })
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal
    ) {
        DocumentResponse response = documentService.upload(file, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List all documents")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documents retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token (no response body)")
    })
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list() {
        return ResponseEntity.ok(documentService.list());
    }

    @Operation(summary = "Get a document by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document found",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "No document with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token (no response body)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.get(id));
    }

    @Operation(summary = "Delete a document", description = "Requires the ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document deleted"),
            @ApiResponse(responseCode = "403", description = "Caller does not have the ADMIN role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No document with that id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token (no response body)")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
