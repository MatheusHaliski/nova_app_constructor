package com.nova.constructor.admin;

import com.nova.constructor.auth.RefreshTokenRepository;
import com.nova.constructor.common.ApiException;
import com.nova.constructor.user.Role;
import com.nova.constructor.user.User;
import com.nova.constructor.user.UserRepository;
import com.nova.constructor.user.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administração de usuários e perfis de acesso (somente ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record RolesUpdate(@NotEmpty(message = "Informe ao menos um perfil.") Set<Role> roles) {}

    public record StatusUpdate(boolean enabled) {}

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public AdminUserController(UserRepository users, RefreshTokenRepository refreshTokens, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<User> result = users.findAll(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("createdAt").descending()));
        return new PageResponse<>(result.map(UserResponse::from).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PutMapping("/{id}/roles")
    @Transactional
    public UserResponse roles(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RolesUpdate body) {
        User user = load(id);
        Set<Role> roles = EnumSet.copyOf(body.roles());
        roles.add(Role.USER);
        boolean losingAdmin = user.getRoles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN);
        if (losingAdmin && id.toString().equals(jwt.getSubject())) {
            throw new ApiException(HttpStatus.CONFLICT, "Você não pode remover o seu próprio perfil de administrador.");
        }
        if (losingAdmin && users.countByRolesContainingAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "O sistema precisa de pelo menos um administrador.");
        }
        user.setRoles(roles);
        refreshTokens.revokeAllForUser(id, clock.instant());
        return UserResponse.from(user);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public UserResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody StatusUpdate body) {
        if (!body.enabled() && id.toString().equals(jwt.getSubject())) {
            throw new ApiException(HttpStatus.CONFLICT, "Você não pode desativar a sua própria conta.");
        }
        User user = load(id);
        user.setEnabled(body.enabled());
        if (!body.enabled()) {
            refreshTokens.revokeAllForUser(id, clock.instant());
        }
        return UserResponse.from(user);
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }
}
