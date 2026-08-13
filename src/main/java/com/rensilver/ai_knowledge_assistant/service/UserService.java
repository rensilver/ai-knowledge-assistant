package com.rensilver.ai_knowledge_assistant.service;

import com.rensilver.ai_knowledge_assistant.dto.UserResponse;
import com.rensilver.ai_knowledge_assistant.entity.Role;
import com.rensilver.ai_knowledge_assistant.entity.UserEntity;
import com.rensilver.ai_knowledge_assistant.exception.LastAdminException;
import com.rensilver.ai_knowledge_assistant.exception.UserNotFoundException;
import com.rensilver.ai_knowledge_assistant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Backs GET /users and PATCH /users/{id}/role. The only path in the
 * application that ever changes a user's role after registration.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse changeRole(UUID id, Role newRole, String actorEmail) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        Role currentRole = user.getRole();
        if (currentRole == newRole) {
            return UserResponse.from(user);
        }

        if (currentRole == Role.ADMIN && newRole != Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.setRole(newRole);
        userRepository.save(user);

        log.info("Role changed: actor={} userId={} from={} to={}", actorEmail, id, currentRole, newRole);

        return UserResponse.from(user);
    }
}
