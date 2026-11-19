package com.nova.constructor.auth;

import com.nova.constructor.user.UserResponse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Contratos da API de autenticação. */
public final class AuthDtos {

    private AuthDtos() {}

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String USERNAME_RULE = "^[a-z0-9_.]{3,30}$";

    /**
     * Cadastro. Pede só o necessário (art. 6º, III): a data de nascimento serve apenas para verificar a idade mínima.
     * O aceite dos termos é uma ação afirmativa: o checkbox chega desmarcado e precisa ser marcado.
     */
    public record RegisterRequest(
            @NotBlank(message = "Informe seu nome.") @Size(max = 120, message = "Use até 120 caracteres.") String name,
            @NotBlank(message = "Escolha um nome de usuário.")
            @Pattern(regexp = USERNAME_RULE, message = "Use de 3 a 30 letras minúsculas, números, ponto ou sublinhado.") String username,
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @NotBlank(message = "Crie uma senha.")
            @Pattern(regexp = PASSWORD_RULE, message = "A senha precisa ter de 8 a 72 caracteres, com letras e números.") String password,
            @NotNull(message = "Informe sua data de nascimento.") @Past(message = "Data inválida.") LocalDate birthDate,
            @AssertTrue(message = "É preciso aceitar os Termos de Uso e a Política de Privacidade.") boolean acceptTerms) {}

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") String email,
            @NotBlank(message = "Informe sua senha.") String password) {}

    public record RefreshRequest(@NotBlank(message = "Refresh token ausente.") String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
}
