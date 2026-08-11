package com.rensilver.ai_knowledge_assistant.rag;

import com.rensilver.ai_knowledge_assistant.dto.SourceReference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Builds a real {@link ChatClient} over a mocked {@link ChatModel} rather than
 * mocking the fluent {@code ChatClient} chain itself — {@code prompt()...call()}
 * returns a different builder type at nearly every step, which makes Mockito
 * deep-stubbing brittle. This way the test exercises the actual fluent API and
 * only fakes the one external boundary: the model call.
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private ChatModel chatModel;

    private ChatClient chatClient;
    private ArgumentCaptor<Prompt> promptCaptor;

    @BeforeEach
    void setUp() {
        // ChatClient merges default options into every request via
        // chatModel.getOptions().mutate() — an unstubbed mock returns null here
        // and NPEs, so every test needs this even ones that don't otherwise care.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        chatClient = ChatClient.builder(chatModel).build();
        promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    }

    private RagService ragServiceRetrieving(List<Document> chunks) {
        ContextRetriever retriever = mock(ContextRetriever.class);
        when(retriever.retrieve(any())).thenReturn(chunks);
        return new RagService(chatClient, retriever, new PromptAugmentationService(), new SimpleMeterRegistry());
    }

    private void stubModelResponse(String text) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    @Test
    void putsRetrievedContextInTheSystemMessageAndKeepsTheUserMessageAsTheRawQuestion() {
        Document chunk = new Document("Deploys go through the staging pipeline first.", Map.of(
                DocumentChunker.DOCUMENT_ID, "doc-1",
                DocumentChunker.FILENAME, "Runbook.pdf",
                DocumentChunker.PAGE, 3
        ));
        RagService ragService = ragServiceRetrieving(List.of(chunk));
        stubModelResponse("Deploys go through staging first, per the runbook.");

        ragService.answer("What is our deployment process?", "conversation-key");

        verify(chatModel).call(promptCaptor.capture());
        Prompt sentPrompt = promptCaptor.getValue();

        // The exact tradeoff CLAUDE.md documents: history should only ever see
        // the bare question, never a copy of the retrieved chunk text.
        assertThat(sentPrompt.getUserMessage().getText()).isEqualTo("What is our deployment process?");
        assertThat(sentPrompt.getSystemMessage().getText())
                .contains("Runbook.pdf (page 3)")
                .contains("Deploys go through the staging pipeline first.");
    }

    @Test
    void returnsTheModelsAnswerAndTheDeduplicatedSourcesOfTheRetrievedChunks() {
        Document chunkA = new Document("First fact.", Map.of(
                DocumentChunker.FILENAME, "Handbook.pdf", DocumentChunker.PAGE, 5));
        Document chunkB = new Document("Same page, different chunk.", Map.of(
                DocumentChunker.FILENAME, "Handbook.pdf", DocumentChunker.PAGE, 5));
        Document chunkC = new Document("Second document.", Map.of(
                DocumentChunker.FILENAME, "Policy.pdf", DocumentChunker.PAGE, 1));
        RagService ragService = ragServiceRetrieving(List.of(chunkA, chunkB, chunkC));
        stubModelResponse("Here is the grounded answer.");

        RagAnswer answer = ragService.answer("question", "conversation-key");

        assertThat(answer.content()).isEqualTo("Here is the grounded answer.");
        assertThat(answer.sources()).containsExactly(
                new SourceReference("Handbook.pdf", 5),
                new SourceReference("Policy.pdf", 1));
    }

    @Test
    void tellsTheModelNothingWasFoundWhenRetrievalIsEmpty() {
        RagService ragService = ragServiceRetrieving(List.of());
        stubModelResponse("The knowledge base does not cover that.");

        RagAnswer answer = ragService.answer("an unanswerable question", "conversation-key");

        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getSystemMessage().getText())
                .contains("No relevant context was found");
        assertThat(answer.sources()).isEmpty();
    }
}
