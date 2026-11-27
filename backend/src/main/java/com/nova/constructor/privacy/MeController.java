package com.nova.constructor.privacy;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.privacy.SettingsDtos.AccountUpdate;
import com.nova.constructor.privacy.SettingsDtos.ConsentUpdate;
import com.nova.constructor.privacy.SettingsDtos.DeleteAccount;
import com.nova.constructor.privacy.SettingsDtos.EmailChange;
import com.nova.constructor.privacy.SettingsDtos.PasswordChange;
import com.nova.constructor.privacy.SettingsDtos.PreferencesUpdate;
import com.nova.constructor.privacy.SettingsDtos.PrivacyUpdate;
import com.nova.constructor.privacy.SettingsDtos.SettingsResponse;
import com.nova.constructor.user.UserRepository;
import com.nova.constructor.user.UserResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Perfil e configurações do usuário autenticado. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final SettingsService settings;
    private final UserRepository users;

    public MeController(SettingsService settings, UserRepository users) {
        this.settings = settings;
        this.users = users;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(id(jwt)).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    @GetMapping("/settings")
    public SettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return settings.get(id(jwt));
    }

    @PutMapping("/account")
    public SettingsResponse account(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountUpdate body) {
        return settings.updateAccount(id(jwt), body);
    }

    @PutMapping("/email")
    public SettingsResponse email(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EmailChange body) {
        return settings.changeEmail(id(jwt), body);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChange body) {
        settings.changePassword(id(jwt), body);
    }

    @PutMapping("/privacy")
    public SettingsResponse privacy(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PrivacyUpdate body) {
        return settings.updatePrivacy(id(jwt), body);
    }

    @PutMapping("/consents/{purpose}")
    public SettingsResponse consent(@AuthenticationPrincipal Jwt jwt, @PathVariable ConsentPurpose purpose,
                                    @RequestBody ConsentUpdate body) {
        return settings.setConsent(id(jwt), purpose, body.granted());
    }

    @PutMapping("/preferences")
    public SettingsResponse preferences(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesUpdate body) {
        return settings.updatePreferences(id(jwt), body);
    }

    @GetMapping("/sessions")
    public java.util.List<SettingsDtos.Session> sessions(@AuthenticationPrincipal Jwt jwt) {
        return settings.sessions(id(jwt), jwt.getClaimAsString("sid"));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void endSession(@AuthenticationPrincipal Jwt jwt, @org.springframework.web.bind.annotation.PathVariable UUID sessionId) {
        settings.endSession(id(jwt), sessionId);
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nova-meus-dados.json\"")
                .body(settings.export(id(jwt)));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccount body) {
        settings.deleteAccount(id(jwt), body);
    }

    private static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
