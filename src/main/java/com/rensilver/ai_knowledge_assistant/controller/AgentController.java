package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.agent.AgentService;
import com.rensilver.ai_knowledge_assistant.dto.ChatRequest;
import com.rensilver.ai_knowledge_assistant.dto.ChatResponse;
import com.rensilver.ai_knowledge_assistant.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V4 tool-calling endpoint. Same request/response shape as {@code /chat}, but
 * the model chooses when to search the knowledge base rather than always being
 * handed one set of retrieved chunks — see {@code AgentService}.
 */
@Tag(name = "Agent", description = "Tool-calling agent: the model chooses when to search the "
        + "knowledge base or external knowledge rather than always being handed retrieved context.")
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "Ask a question via the tool-calling agent",
            description = "Same request/response shape as POST /chat, but the model decides "
                    + "whether to call searchDocuments/listDocuments/searchExternalKnowledge rather "
                    + "than always being handed one fixed set of retrieved chunks.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer produced",
                    content = @Content(schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.ok(agentService.chat(request, principal.getUsername()));
    }
}
