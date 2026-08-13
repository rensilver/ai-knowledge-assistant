package com.rensilver.ai_knowledge_assistant.config;

import com.rensilver.ai_knowledge_assistant.dto.RegisterRequest;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates exactly one ADMIN user from ADMIN_BOOTSTRAP_EMAIL /
 * ADMIN_BOOTSTRAP_PASSWORD on startup, so a fresh deployment always has a
 * working admin without anyone touching the database directly. A no-op
 * unless both are set; a no-op again once that email already has a user, so
 * it's safe to leave the env vars in place across restarts.
 *
 * <p>Validated with the same constraints RegisterRequest enforces on
 * self-registration, so a typo'd env var is skipped with a warning instead
 * of crashing the app at boot.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminBootstrapRunner(
            @Value("${app.admin.bootstrap-email:}") String bootstrapEmail,
            @Value("${app.admin.bootstrap-password:}") String bootstrapPassword,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Validator validator
    ) {
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
            return;
        }

        if (userRepository.existsByEmail(bootstrapEmail)) {
            log.info("Admin bootstrap skipped: a user with email {} already exists", bootstrapEmail);
            return;
        }

        RegisterRequest candidate = new RegisterRequest("Admin", bootstrapEmail, bootstrapPassword);
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(candidate);
        if (!violations.isEmpty()) {
            log.warn("Admin bootstrap skipped: ADMIN_BOOTSTRAP_EMAIL/ADMIN_BOOTSTRAP_PASSWORD failed validation ({} issue(s))",
                    violations.size());
            return;
        }

        UserEntity admin = UserEntity.builder()
                .name("Admin")
                .email(bootstrapEmail)
                .password(passwordEncoder.encode(bootstrapPassword))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        log.info("Bootstrapped initial admin user: {}", bootstrapEmail);
    }
}
