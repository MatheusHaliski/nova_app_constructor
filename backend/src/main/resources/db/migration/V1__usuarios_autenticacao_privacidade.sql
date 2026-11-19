-- Usuários, perfis de acesso, sessões (refresh tokens) e consentimentos (LGPD).
CREATE TABLE users (
    id                    CHAR(36)     NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    username              VARCHAR(30)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    birth_date            DATE         NOT NULL,
    bio                   VARCHAR(280),
    password_hash         VARCHAR(100) NOT NULL,
    profile_visibility    VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE',
    theme                 VARCHAR(10)  NOT NULL DEFAULT 'SYSTEM',
    language              VARCHAR(10)  NOT NULL DEFAULT 'pt-BR',
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP(6) NULL,
    terms_version         VARCHAR(20)  NOT NULL,
    terms_accepted_at     TIMESTAMP(6) NOT NULL,
    created_at            TIMESTAMP(6) NOT NULL,
    updated_at            TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE user_roles (
    user_id CHAR(36)    NOT NULL,
    role    VARCHAR(20) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id         CHAR(36)     NOT NULL,
    user_id    CHAR(36)     NOT NULL,
    family_id  CHAR(36)     NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

-- Histórico imutável de consentimentos: prova (art. 8º, §2º) e responsabilização (art. 6º, X).
CREATE TABLE consent_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     CHAR(36)     NOT NULL,
    purpose     VARCHAR(40)  NOT NULL,
    granted     BOOLEAN      NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_consent_events PRIMARY KEY (id),
    CONSTRAINT fk_consent_events_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_consent_events_user ON consent_events (user_id, occurred_at);
