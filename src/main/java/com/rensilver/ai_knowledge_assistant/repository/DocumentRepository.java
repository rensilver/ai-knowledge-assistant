package com.rensilver.ai_knowledge_assistant.repository;

import com.rensilver.ai_knowledge_assistant.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findAllByOrderByCreatedAtDesc();
}
