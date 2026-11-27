package com.nova.constructor.privacy;

import com.nova.constructor.auth.AuthService;
import com.nova.constructor.auth.RefreshTokenRepository;
import com.nova.constructor.common.ApiException;
import com.nova.constructor.privacy.SettingsDtos.Account;
import com.nova.constructor.privacy.SettingsDtos.AccountUpdate;
import com.nova.constructor.privacy.SettingsDtos.ConsentState;
import com.nova.constructor.privacy.SettingsDtos.DeleteAccount;
import com.nova.constructor.privacy.SettingsDtos.EmailChange;
import com.nova.constructor.privacy.SettingsDtos.PasswordChange;
import com.nova.constructor.privacy.SettingsDtos.Preferences;
import com.nova.constructor.privacy.SettingsDtos.PreferencesUpdate;
import com.nova.constructor.privacy.SettingsDtos.Privacy;
import com.nova.constructor.privacy.SettingsDtos.SettingsResponse;
import com.nova.constructor.privacy.SettingsDtos.Terms;
import com.nova.constructor.user.Role;
import com.nova.constructor.user.User;
import com.nova.constructor.user.UserRepository;
import com.nova.constructor.user.UserResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.nova.constructor.privacy.SettingsDtos.Session;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configurações do perfil: cada operação realiza um direito do titular (LGPD, art. 18). */
@Service
public class SettingsService {

    private final UserRepository users;
    private final ConsentEventRepository consents;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    public SettingsService(UserRepository users, ConsentEventRepository consents, RefreshTokenRepository refreshTokens,
                           PasswordEncoder passwordEncoder, Clock clock, org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.users = users;
        this.consents = consents;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get(UUID userId) {
        return toResponse(load(userId));
    }

    @Transactional
    public SettingsResponse updateAccount(UUID userId, AccountUpdate update) {
        User user = load(userId);
        if (!update.username().equals(user.getUsername()) && users.existsByUsername(update.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Este nome de usuário já está em uso.");
        }
        if (Period.between(update.birthDate(), LocalDate.now(clock)).getYears() < AuthService.MINIMUM_AGE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "O NOVA é destinado a maiores de " + AuthService.MINIMUM_AGE + " anos.");
        }
        String bio = update.bio() == null || update.bio().isBlank() ? null : update.bio().trim();
        user.updateAccount(update.name().trim(), update.username(), bio, update.birthDate());
        user.updateLinks(update.websiteUrl() == null || update.websiteUrl().isBlank() ? null : update.websiteUrl().trim(),
                update.githubUsername() == null || update.githubUsername().isBlank() ? null : update.githubUsername().trim());
        return toResponse(user);
    }

    @Transactional
    public SettingsResponse changeEmail(UUID userId, EmailChange change) {
        User user = load(userId);
        checkPassword(user, change.currentPassword());
        String email = AuthService.normalizeEmail(change.newEmail());
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail.");
        }
        user.changeEmail(email);
        return toResponse(user);
    }

    /** Troca a senha e encerra todas as sessões abertas. */
    @Transactional
    public void changePassword(UUID userId, PasswordChange change) {
        User user = load(userId);
        checkPassword(user, change.currentPassword());
        user.changePassword(passwordEncoder.encode(change.newPassword()), clock.instant());
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    @Transactional
    public SettingsResponse updatePrivacy(UUID userId, SettingsDtos.PrivacyUpdate update) {
        User user = load(userId);
        user.setProfileVisibility(update.profileVisibility());
        return toResponse(user);
    }

    /** Concede ou revoga um consentimento; cada mudança gera um evento (prova e responsabilização). */
    @Transactional
    public SettingsResponse setConsent(UUID userId, ConsentPurpose purpose, boolean granted) {
        User user = load(userId);
        boolean current = currentConsents(userId).get(purpose).granted();
        if (current != granted) {
            consents.save(new ConsentEvent(userId, purpose, granted, clock.instant(), load(userId).getTermsVersion(), "SETTINGS"));
        }
        return toResponse(user);
    }

    @Transactional
    public SettingsResponse updatePreferences(UUID userId, PreferencesUpdate update) {
        User user = load(userId);
        user.setTheme(update.theme());
        user.setLanguage(update.language());
        return toResponse(user);
    }

    /** Portabilidade e acesso (art. 18, II e V): todos os dados em formato legível por máquina. */
    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID userId) {
        User user = load(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", clock.instant());
        data.put("controller", "NOVA App Constructor");
        data.put("user", UserResponse.from(user));
        data.put("settings", toResponse(user));
        data.put("consentHistory", consents.findByUserIdOrderByOccurredAtAscIdAsc(userId).stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("purpose", e.getPurpose());
                    m.put("granted", e.isGranted());
                    m.put("at", e.getOccurredAt());
                    m.put("policyVersion", e.getPolicyVersion());
                    m.put("channel", e.getChannel());
                    return m;
                })
                .toList());
        data.put("sessions", sessions(userId, null));
        String id = userId.toString();
        data.put("projects", rows("select id, name, slug, status, visibility, license, current_version, created_at, updated_at, last_opened_at from projects where owner_id = ? order by created_at", id));
        data.put("sharing", rows("select l.project_id, l.label, l.created_at, l.expires_at, l.revoked_at, l.last_used_at from share_links l join projects p on p.id = l.project_id where p.owner_id = ? order by l.created_at", id));
        data.put("stars", rows("select project_id, created_at from project_stars where user_id = ? order by created_at", id));
        data.put("comments", rows("select project_id, body, hidden, created_at from project_comments where author_id = ? order by created_at", id));
        data.put("reports", rows("select project_id, comment_id, reason, details, status, created_at from reports where reporter_id = ? order by created_at", id));
        data.put("suggestions", rows("select project_id, title, body, status, created_at from project_suggestions where author_id = ? order by created_at", id));
        data.put("pinnedProjects", rows("select project_id, position from pinned_projects where user_id = ? order by position", id));
        data.put("notifications", rows("select kind, message, link, read_at, created_at from notifications where user_id = ? order by created_at", id));
        return data;
    }

    /** Eliminação (art. 18, VI): exige senha e confirmação; remove sessões, consentimentos e a conta. */
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccount request) {
        User user = load(userId);
        checkPassword(user, request.currentPassword());
        if (user.getRoles().contains(Role.ADMIN) && users.countByRolesContainingAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Você é o único administrador. Promova outra pessoa antes de excluir a conta.");
        }
        refreshTokens.deleteByUserId(userId);
        consents.deleteByUserId(userId);
        users.delete(user);
    }

    private List<Map<String, Object>> rows(String sql, String userId) {
        return jdbc.sql(sql).param(userId).query().listOfRows();
    }

    /** Sessões abertas: uma por família de refresh tokens com token ainda válido. */
    @Transactional(readOnly = true)
    public List<Session> sessions(UUID userId, String currentSessionId) {
        Instant now = clock.instant();
        Map<UUID, List<com.nova.constructor.auth.RefreshToken>> byFamily = new LinkedHashMap<>();
        for (com.nova.constructor.auth.RefreshToken t : refreshTokens.findByUserIdOrderByCreatedAtAsc(userId)) {
            byFamily.computeIfAbsent(t.getFamilyId(), k -> new java.util.ArrayList<>()).add(t);
        }
        List<Session> out = new java.util.ArrayList<>();
        for (Map.Entry<UUID, List<com.nova.constructor.auth.RefreshToken>> e : byFamily.entrySet()) {
            List<com.nova.constructor.auth.RefreshToken> tokens = e.getValue();
            com.nova.constructor.auth.RefreshToken last = tokens.getLast();
            if (!last.isActive(now)) {
                continue;
            }
            out.add(new Session(e.getKey().toString(), device(last.getUserAgent()), tokens.getFirst().getCreatedAt(), last.getCreatedAt(),
                    e.getKey().toString().equals(currentSessionId)));
        }
        out.sort((a, b) -> b.lastActiveAt().compareTo(a.lastActiveAt()));
        return out;
    }

    @Transactional
    public void endSession(UUID userId, UUID sessionId) {
        boolean mine = refreshTokens.findByUserIdOrderByCreatedAtAsc(userId).stream().anyMatch(t -> t.getFamilyId().equals(sessionId));
        if (!mine) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Sessão não encontrada.");
        }
        refreshTokens.revokeFamily(sessionId, clock.instant());
    }

    /** Resumo do navegador e do sistema, sem guardar nem mostrar o texto completo do User-Agent. */
    static String device(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Dispositivo desconhecido";
        }
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : ua.startsWith("node") || ua.contains("undici") ? "Servidor" : "Navegador";
        String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS") ? "macOS" : ua.contains("Linux") ? "Linux" : null;
        return os == null ? browser : browser + " no " + os;
    }

    private Map<ConsentPurpose, ConsentState> currentConsents(UUID userId) {
        Map<ConsentPurpose, ConsentState> state = new EnumMap<>(ConsentPurpose.class);
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            state.put(purpose, new ConsentState(false, null));
        }
        for (ConsentEvent event : consents.findByUserIdOrderByOccurredAtAscIdAsc(userId)) {
            state.put(event.getPurpose(), new ConsentState(event.isGranted(), event.getOccurredAt()));
        }
        return state;
    }

    private SettingsResponse toResponse(User user) {
        return new SettingsResponse(
                new Account(user.getName(), user.getUsername(), user.getEmail(), user.isEmailVerified(), user.getBirthDate(), user.getBio(),
                        user.getWebsiteUrl(), user.getGithubUsername(), user.getLastLoginAt(), user.getPasswordChangedAt()),
                new Privacy(user.getProfileVisibility(), currentConsents(user.getId())),
                new Preferences(user.getTheme(), user.getLanguage()),
                new Terms(user.getTermsVersion(), user.getTermsAcceptedAt()));
    }

    private void checkPassword(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Senha atual incorreta.");
        }
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    Instant now() {
        return clock.instant();
    }
}
