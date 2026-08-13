package com.rensilver.ai_knowledge_assistant.config;

import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void doesNothingWhenBootstrapEmailIsBlank() throws Exception {
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner("", "irrelevant-but-long-enough", userRepository, passwordEncoder, validator);

        runner.run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenBootstrapPasswordIsBlank() throws Exception {
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner("admin@example.com", "", userRepository, passwordEncoder, validator);

        runner.run(null);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenAUserWithThatEmailAlreadyExists() throws Exception {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                "admin@example.com", "correct-password", userRepository, passwordEncoder, validator);

        runner.run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void skipsBootstrapWhenTheCredentialsFailValidation() throws Exception {
        when(userRepository.existsByEmail("not-an-email")).thenReturn(false);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                "not-an-email", "correct-password", userRepository, passwordEncoder, validator);

        runner.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAnAdminUserWhenCredentialsAreValidAndNoUserExistsYet() throws Exception {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct-password")).thenReturn("bcrypt-hash");
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                "admin@example.com", "correct-password", userRepository, passwordEncoder, validator);

        runner.run(null);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    }
}
