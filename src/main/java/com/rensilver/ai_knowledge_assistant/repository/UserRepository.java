package com.rensilver.ai_knowledge_assistant.repository;

import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Used by UserDetailsServiceImpl on every authenticated request (via
     * JwtFilter) and by AuthService on login/registration. Email is the
     * unique login identifier — see the unique constraint on
     * users.email in the V1 migration.
     */
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
