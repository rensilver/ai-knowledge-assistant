package com.rensilver.ai_knowledge_assistant.controller;

import com.rensilver.ai_knowledge_assistant.chat.ChatService;
import com.rensilver.ai_knowledge_assistant.dto.ChatResponse;
import com.rensilver.ai_knowledge_assistant.security.JwtService;
import com.rensilver.ai_knowledge_assistant.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    // See DocumentControllerTest: JwtFilter is picked up as a servlet Filter,
    // so its constructor dependencies need to at least exist as beans.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser(username = "ada@example.com")
    void chatReturnsTheAnswerAndConversationId() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(chatService.chat(any(), eq("ada@example.com")))
                .thenReturn(new ChatResponse("The deploy pipeline goes through staging first.", conversationId, List.of()));

        mockMvc.perform(post("/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "What is our deployment process?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The deploy pipeline goes through staging first."))
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()));
    }

    @Test
    @WithMockUser
    void chatRejectsABlankMessage() throws Exception {
        mockMvc.perform(post("/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
