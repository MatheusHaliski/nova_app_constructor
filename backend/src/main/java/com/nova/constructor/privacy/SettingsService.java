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

    public SettingsService(UserRepository users, ConsentEventRepository consents, RefreshTokenRepository refreshTokens,
                           PasswordEncoder passwordEncoder, Clock clock) {
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
        user.changePassword(passwordEncoder.encode(change.newPassword()));
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
            consents.save(new ConsentEvent(userId, purpose, granted, clock.instant()));
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
                .map(e -> Map.of("purpose", e.getPurpose(), "granted", e.isGranted(), "at", e.getOccurredAt()))
                .toList());
        data.put("sharing", List.of());
        data.put("projects", List.of());
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
                new Account(user.getName(), user.getUsername(), user.getEmail(), user.isEmailVerified(), user.getBirthDate(), user.getBio()),
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
