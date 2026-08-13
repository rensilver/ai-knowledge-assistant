package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.exception.LastAdminException;
import com.rensilver.ai_knowledge_assistant.exception.UserNotFoundException;
import com.rensilver.ai_knowledge_assistant.security.JwtService;
import com.rensilver.ai_knowledge_assistant.security.UserDetailsServiceImpl;
import com.rensilver.ai_knowledge_assistant.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers request/response mapping and exception translation only.
 * @PreAuthorize enforcement is not testable here — see AdminRoleManagementIT.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // JwtFilter is a servlet Filter, so @WebMvcTest picks it up even though
    // its own dependencies fall outside the slice; see DocumentControllerTest.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser
    void listReturnsAllUsers() throws Exception {
        UserResponse response = new UserResponse(UUID.randomUUID(), "Ada", "ada@example.com", "USER", Instant.now());
        when(userService.list()).thenReturn(List.of(response));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("ada@example.com"));
    }

    @Test
    @WithMockUser(username = "root@example.com")
    void changeRoleReturnsTheUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse(id, "Ada", "ada@example.com", "ADMIN", Instant.now());
        when(userService.changeRole(eq(id), any(), eq("root@example.com"))).thenReturn(response);

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser
    void changeRoleReturns404WhenTheUserDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.changeRole(eq(id), any(), any())).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void changeRoleReturns409WhenDemotingTheLastAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.changeRole(eq(id), any(), any())).thenThrow(new LastAdminException());

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void changeRoleReturns400WhenRoleIsMissing() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/users/{id}/role", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
