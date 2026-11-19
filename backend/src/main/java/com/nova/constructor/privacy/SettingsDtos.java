package com.nova.constructor.privacy;

import com.nova.constructor.auth.AuthDtos;
import com.nova.constructor.user.ProfileVisibility;
import com.nova.constructor.user.Theme;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Contratos da página de configurações do perfil (padrão LGPD: Conta, Privacidade, Aparência, Idioma, Seus dados). */
public final class SettingsDtos {

    private SettingsDtos() {}

    public static final String LANGUAGE_RULE = "^(pt-BR|en-US|es-ES)$";

    public record Account(String name, String username, String email, boolean emailVerified, LocalDate birthDate, String bio) {}

    public record ConsentState(boolean granted, Instant updatedAt) {}

    public record Privacy(ProfileVisibility profileVisibility, Map<ConsentPurpose, ConsentState> consents) {}

    public record Preferences(Theme theme, String language) {}

    public record Terms(String version, Instant acceptedAt) {}

    public record SettingsResponse(Account account, Privacy privacy, Preferences preferences, Terms terms) {}

    /** Correção de dados (art. 18, III). */
    public record AccountUpdate(
            @NotBlank(message = "Informe seu nome.") @Size(max = 120, message = "Use até 120 caracteres.") String name,
            @NotBlank(message = "Escolha um nome de usuário.")
            @Pattern(regexp = AuthDtos.USERNAME_RULE, message = "Use de 3 a 30 letras minúsculas, números, ponto ou sublinhado.") String username,
            @Size(max = 280, message = "A biografia pode ter até 280 caracteres.") String bio,
            @NotNull(message = "Informe sua data de nascimento.") @Past(message = "Data inválida.") LocalDate birthDate) {}

    /** Troca de e-mail exige a senha atual (reautenticação). */
    public record EmailChange(
            @NotBlank(message = "Informe o novo e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String newEmail,
            @NotBlank(message = "Confirme com sua senha atual.") String currentPassword) {}

    public record PasswordChange(
            @NotBlank(message = "Informe sua senha atual.") String currentPassword,
            @NotBlank(message = "Crie uma nova senha.")
            @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = "A senha precisa ter de 8 a 72 caracteres, com letras e números.") String newPassword) {}

    public record PrivacyUpdate(@NotNull(message = "Escolha quem pode ver seu perfil.") ProfileVisibility profileVisibility) {}

    public record ConsentUpdate(boolean granted) {}

    public record PreferencesUpdate(
            @NotNull(message = "Escolha um tema.") Theme theme,
            @NotBlank @Pattern(regexp = LANGUAGE_RULE, message = "Idioma não suportado.") String language) {}

    /** Exclusão de conta (art. 18, VI): senha atual e confirmação digitada. */
    public record DeleteAccount(
            @NotBlank(message = "Confirme com sua senha atual.") String currentPassword,
            @NotBlank @Pattern(regexp = "^EXCLUIR$", message = "Digite EXCLUIR para confirmar.") String confirmation) {}
}
