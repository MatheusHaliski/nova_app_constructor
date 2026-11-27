package com.nova.constructor.auth;

import com.nova.constructor.config.NovaProperties;
import com.nova.constructor.user.Role;
import com.nova.constructor.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite access tokens JWT RS256 e refresh tokens opacos. */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final NovaProperties props;
    private final RefreshTokenRepository refreshTokens;

    public TokenService(JwtEncoder encoder, NovaProperties props, RefreshTokenRepository refreshTokens) {
        this.encoder = encoder;
        this.props = props;
        this.refreshTokens = refreshTokens;
    }

    /** @param sessionId família do refresh token: identifica a sessão (claim sid) para listar e encerrar sessões */
    public String accessToken(User user, UUID sessionId, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Role::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(props.jwt().keyId()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Cria e persiste um refresh token; retorna o valor em texto (só existe na resposta). */
    public String newRefreshToken(User user, UUID familyId, Instant now, String userAgent) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), familyId, hash(value), now, now.plus(props.jwt().refreshTokenTtl()), userAgent));
        return value;
    }

    public long accessTokenSeconds() {
        return props.jwt().accessTokenTtl().toSeconds();
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
