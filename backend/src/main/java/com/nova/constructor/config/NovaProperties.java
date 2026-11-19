package com.nova.constructor.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configurações próprias do NOVA (prefixo {@code nova}). */
@ConfigurationProperties("nova")
public record NovaProperties(Jwt jwt, Login login, Cors cors, Admin admin, Connectors connectors,
                             @DefaultValue("2026-11") String termsVersion) {

    /**
     * Tokens JWT RS256 emitidos pelo próprio NOVA.
     *
     * @param privateKey chave privada RSA em PEM (PKCS#8); vem de segredo, nunca do código
     * @param publicKey chave pública RSA em PEM (X.509)
     * @param allowEphemeralKeys gera um par de chaves temporário quando nenhum é informado (só desenvolvimento)
     */
    public record Jwt(
            @DefaultValue("nova-app-constructor") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("7d") Duration refreshTokenTtl,
            String privateKey,
            String publicKey,
            @DefaultValue("nova-key-1") String keyId,
            @DefaultValue("true") boolean allowEphemeralKeys) {}

    /** Bloqueio temporário após tentativas de login falhas. */
    public record Login(@DefaultValue("5") int maxFailedAttempts, @DefaultValue("15m") Duration lockDuration) {}

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}

    /** Administrador criado na primeira inicialização, se informado. */
    public record Admin(String email, String password, @DefaultValue("Administrador") String name) {}

    /**
     * Conectores opcionais. O MySQL é sempre a fonte central de dados; estes serviços só complementam e ficam
     * desligados por padrão.
     */
    public record Connectors(
            @DefaultValue Connector redis,
            @DefaultValue Connector opensearch,
            @DefaultValue Connector s3,
            @DefaultValue Connector cassandra) {}

    /** @param enabled liga o conector; @param url endereço do serviço (sem credenciais, que vêm de segredos) */
    public record Connector(@DefaultValue("false") boolean enabled, String url) {}
}
