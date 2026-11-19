package com.nova.constructor.auth;

import com.nova.constructor.auth.AuthDtos.LoginRequest;
import com.nova.constructor.auth.AuthDtos.RegisterRequest;
import com.nova.constructor.auth.AuthDtos.TokenResponse;
import com.nova.constructor.common.ApiException;
import com.nova.constructor.config.NovaProperties;
import com.nova.constructor.user.Role;
import com.nova.constructor.user.User;
import com.nova.constructor.user.UserRepository;
import com.nova.constructor.user.UserResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Idade mínima definida nos Termos (ECA Digital: menores exigem regras próprias). */
    public static final int MINIMUM_AGE = 18;
    private static final String INVALID_LOGIN = "E-mail ou senha inválidos.";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final NovaProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
                       PasswordEncoder passwordEncoder, NovaProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        Instant now = clock.instant();
        String email = normalizeEmail(request.email());
        if (Period.between(request.birthDate(), LocalDate.now(clock)).getYears() < MINIMUM_AGE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "O NOVA é destinado a maiores de " + MINIMUM_AGE + " anos.");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail.");
        }
        if (users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Este nome de usuário já está em uso.");
        }
        User user = new User(request.name().trim(), request.username(), email, request.birthDate(),
                passwordEncoder.encode(request.password()), Set.of(Role.USER), props.termsVersion(), now);
        users.save(user);
        return issue(user, UUID.randomUUID(), now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest request) {
        Instant now = clock.instant();
        User user = users.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN));
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, "Conta bloqueada temporariamente por tentativas inválidas. Tente mais tarde.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.registerFailedLogin(props.login().maxFailedAttempts(), now.plus(props.login().lockDuration()));
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Conta desativada. Fale com o suporte.");
        }
        user.registerSuccessfulLogin();
        return issue(user, UUID.randomUUID(), now);
    }

    /** Troca o refresh token por um novo (rotação). Reuso de token já trocado revoga a família inteira. */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Faça login novamente."));
        if (current.getRevokedAt() != null) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessão encerrada por segurança. Faça login novamente.");
        }
        if (!current.isActive(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Faça login novamente.");
        }
        User user = users.findById(current.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Faça login novamente."));
        current.revoke(now);
        return issue(user, current.getFamilyId(), now);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    private TokenResponse issue(User user, UUID familyId, Instant now) {
        return new TokenResponse(tokens.accessToken(user, now), tokens.newRefreshToken(user, familyId, now),
                "Bearer", tokens.accessTokenSeconds(), UserResponse.from(user));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
