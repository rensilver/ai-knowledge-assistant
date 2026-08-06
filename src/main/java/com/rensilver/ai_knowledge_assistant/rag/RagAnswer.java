package com.rensilver.ai_knowledge_assistant.rag;

import java.util.List;

public record RagAnswer(String content, List<String> sources) {
}
