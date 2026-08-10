package com.rensilver.ai_knowledge_assistant.rag;

import com.rensilver.ai_knowledge_assistant.dto.SourceReference;

import java.util.List;

public record RagAnswer(String content, List<SourceReference> sources) {
}
