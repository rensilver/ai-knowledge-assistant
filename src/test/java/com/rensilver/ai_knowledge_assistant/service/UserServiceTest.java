package com.rensilver.ai_knowledge_assistant.service;

import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.exception.LastAdminException;
import com.rensilver.ai_knowledge_assistant.exception.UserNotFoundException;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    private UserEntity user(UUID id, Role role) {
        return UserEntity.builder()
                .id(id)
                .name("Ada Lovelace")
                .email("ada@example.com")
                .password("bcrypt-hash")
                .role(role)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void changeRoleThrowsWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(id, Role.ADMIN, "admin@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void changeRoleIsANoOpWhenRoleIsUnchanged() {
        UUID id = UUID.randomUUID();
        UserEntity existing = user(id, Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        UserResponse response = userService.changeRole(id, Role.USER, "admin@example.com");

        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).countByRole(any());
    }

    @Test
    void changeRoleRejectsDemotingTheLastRemainingAdmin() {
        UUID id = UUID.randomUUID();
        UserEntity lastAdmin = user(id, Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.changeRole(id, Role.USER, "admin@example.com"))
                .isInstanceOf(LastAdminException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changeRoleDemotesAnAdminWhenAnotherAdminRemains() {
        UUID id = UUID.randomUUID();
        UserEntity admin = user(id, Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        UserResponse response = userService.changeRole(id, Role.USER, "admin@example.com");

        assertThat(response.role()).isEqualTo("USER");
        assertThat(admin.getRole()).isEqualTo(Role.USER);
        verify(userRepository).save(admin);
    }

    @Test
    void changeRolePromotesAUserToAdminWithoutCheckingAdminCount() {
        UUID id = UUID.randomUUID();
        UserEntity plainUser = user(id, Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(plainUser));

        UserResponse response = userService.changeRole(id, Role.ADMIN, "admin@example.com");

        assertThat(response.role()).isEqualTo("ADMIN");
        verify(userRepository).save(plainUser);
        verify(userRepository, never()).countByRole(any());
    }

    @Test
    void listReturnsAllUsersMappedToResponses() {
        UserEntity a = user(UUID.randomUUID(), Role.ADMIN);
        UserEntity b = user(UUID.randomUUID(), Role.USER);
        when(userRepository.findAll()).thenReturn(List.of(a, b));

        List<UserResponse> result = userService.list();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::role).containsExactlyInAnyOrder("ADMIN", "USER");
    }
}
