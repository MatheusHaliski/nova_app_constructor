package com.nova.constructor.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Dados de um usuário para a interface (nunca inclui hash de senha). */
public record UserResponse(UUID id, String name, String username, String email, Set<Role> roles, boolean enabled,
                           Theme theme, String language, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getUsername(), user.getEmail(), user.getRoles(),
                user.isEnabled(), user.getTheme(), user.getLanguage(), user.getCreatedAt());
    }
}
