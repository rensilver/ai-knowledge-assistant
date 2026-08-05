package com.rensilver.ai_knowledge_assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code users} table. Field names here are what
 * {@code UserDetailsServiceImpl} relies on ({@code email}, {@code password},
 * {@code role}) — keep them in sync if either side changes.
 *
 * <p>{@code password} always stores a BCrypt hash, never plaintext — it's
 * set exclusively through {@code AuthService} using the
 * {@code PasswordEncoder} bean from {@code SecurityConfig}.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash — never the raw password. Column sized generously since
     * BCrypt hashes are a fixed ~60 chars but future encoders may differ.
     */
    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
