package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.document.DocumentService;
import com.rensilver.ai_knowledge_assistant.dto.DocumentResponse;
import com.rensilver.ai_knowledge_assistant.entity.DocumentStatus;
import com.rensilver.ai_knowledge_assistant.exception.DocumentNotFoundException;
import com.rensilver.ai_knowledge_assistant.exception.UnsupportedFileTypeException;
import com.rensilver.ai_knowledge_assistant.security.JwtService;
import com.rensilver.ai_knowledge_assistant.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers request/response mapping and exception translation for the one
 * untested controller named in CLAUDE.md's next steps. Runs with Boot's
 * default slice-test security (not the app's real {@code SecurityConfig} —
 * that's exercised end to end by {@link AuthControllerIT}), so only
 * authentication via {@code @WithMockUser} is asserted here, not the
 * production JWT/authorization rules.
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    // JwtFilter is a servlet Filter, so @WebMvcTest picks it up even though
    // its own dependencies (plain @Service beans) fall outside the slice;
    // these just need to exist for the filter to construct, not do anything
    // — @WithMockUser bypasses the filter's own token handling entirely.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser(username = "ada@example.com")
    void uploadReturnsCreatedWithTheStoredDocument() throws Exception {
        DocumentResponse response = new DocumentResponse(
                UUID.randomUUID(), "policy.pdf", "application/pdf", 123L,
                DocumentStatus.PROCESSING.name(), "ada@example.com", Instant.now());
        when(documentService.upload(any(), eq("ada@example.com"))).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.pdf", "application/pdf", "%PDF-1.4".getBytes());

        mockMvc.perform(multipart("/documents/upload").file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("policy.pdf"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @WithMockUser(username = "ada@example.com")
    void uploadOfAnUnsupportedFileTypeReturnsBadRequest() throws Exception {
        when(documentService.upload(any(), eq("ada@example.com")))
                .thenThrow(new UnsupportedFileTypeException("text/plain"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/documents/upload").file(file).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    void listReturnsAllDocuments() throws Exception {
        DocumentResponse response = new DocumentResponse(
                UUID.randomUUID(), "handbook.pdf", "application/pdf", 42L,
                DocumentStatus.COMPLETED.name(), "ada@example.com", Instant.now());
        when(documentService.list()).thenReturn(List.of(response));

        mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename").value("handbook.pdf"));
    }

    @Test
    @WithMockUser
    void getReturns404WhenTheDocumentDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.get(id)).thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get("/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser
    void getReturnsTheDocumentWhenItExists() throws Exception {
        UUID id = UUID.randomUUID();
        DocumentResponse response = new DocumentResponse(
                id, "policy.pdf", "application/pdf", 100L,
                DocumentStatus.COMPLETED.name(), "ada@example.com", Instant.now());
        when(documentService.get(id)).thenReturn(response);

        mockMvc.perform(get("/documents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
