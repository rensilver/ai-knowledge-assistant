package com.rensilver.ai_knowledge_assistant.service;

import com.rensilver.ai_knowledge_assistant.dto.AuthResponse;
import com.rensilver.ai_knowledge_assistant.dto.LoginRequest;
import com.rensilver.ai_knowledge_assistant.dto.RegisterRequest;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.exception.EmailAlreadyInUseException;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import com.rensilver.ai_knowledge_assistant.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void registerRejectsAnEmailAlreadyInUse() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("Ada", "ada@example.com", "password123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyInUseException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerHashesThePasswordAndReturnsAToken() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("signed-jwt");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        RegisterRequest request = new RegisterRequest("Ada Lovelace", "ada@example.com", "password123");
        AuthResponse response = authService.register(request);

        ArgumentCaptor<UserEntity> savedCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(savedCaptor.capture());
        UserEntity saved = savedCaptor.getValue();
        assertThat(saved.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(saved.getRole()).isEqualTo(Role.USER);

        assertThat(response.token()).isEqualTo("signed-jwt");
        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.name()).isEqualTo("Ada Lovelace");
    }

    @Test
    void loginRejectsBadCredentialsWithoutLeakingWhetherTheEmailExists() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        LoginRequest request = new LoginRequest("ada@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void loginReturnsATokenOnValidCredentials() {
        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .name("Ada Lovelace")
                .email("ada@example.com")
                .password("bcrypt-hash")
                .role(Role.ADMIN)
                .build();
        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("signed-jwt");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        AuthResponse response = authService.login(new LoginRequest("ada@example.com", "password123"));

        assertThat(response.token()).isEqualTo("signed-jwt");
        assertThat(response.role()).isEqualTo("ADMIN");
    }
}
