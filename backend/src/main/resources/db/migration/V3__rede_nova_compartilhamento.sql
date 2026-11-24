-- Rede NOVA: visibilidade, licença, fork, arquivos ocultos, links, estrelas, comentários, notificações e denúncias.
ALTER TABLE projects ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE projects ADD COLUMN license VARCHAR(30) NULL;
ALTER TABLE projects ADD COLUMN published_at TIMESTAMP(6) NULL;
ALTER TABLE projects ADD COLUMN forked_from_id CHAR(36) NULL;
ALTER TABLE projects ADD COLUMN forked_from_label VARCHAR(140) NULL;
CREATE INDEX ix_projects_visibility ON projects (visibility, status, published_at);

-- Arquivos e pastas que só o dono vê, mesmo com o projeto compartilhado.
CREATE TABLE project_hidden_paths (
    project_id CHAR(36)     NOT NULL,
    path       VARCHAR(400) NOT NULL,
    CONSTRAINT pk_project_hidden_paths PRIMARY KEY (project_id, path),
    CONSTRAINT fk_hidden_paths_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

-- Link de compartilhamento: só o hash do token é guardado.
CREATE TABLE share_links (
    id         CHAR(36)     NOT NULL,
    project_id CHAR(36)     NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_used_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_share_links PRIMARY KEY (id),
    CONSTRAINT uk_share_links_hash UNIQUE (token_hash),
    CONSTRAINT fk_share_links_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE project_stars (
    project_id CHAR(36)     NOT NULL,
    user_id    CHAR(36)     NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_project_stars PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_stars_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_stars_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Comentário de quem excluiu a conta fica sem autor ("usuário removido").
CREATE TABLE project_comments (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    project_id CHAR(36)      NOT NULL,
    author_id  CHAR(36)      NULL,
    body       VARCHAR(2000) NOT NULL,
    hidden     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_project_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX ix_comments_project ON project_comments (project_id, created_at);

CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    CHAR(36)     NOT NULL,
    kind       VARCHAR(30)  NOT NULL,
    message    VARCHAR(300) NOT NULL,
    link       VARCHAR(200) NULL,
    read_at    TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_notifications_user ON notifications (user_id, read_at, created_at);

CREATE TABLE reports (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id CHAR(36)      NULL,
    project_id  CHAR(36)      NOT NULL,
    comment_id  BIGINT        NULL,
    reason      VARCHAR(30)   NOT NULL,
    details     VARCHAR(1000) NULL,
    status      VARCHAR(20)   NOT NULL,
    resolution  VARCHAR(500)  NULL,
    resolved_by CHAR(36)      NULL,
    created_at  TIMESTAMP(6)  NOT NULL,
    resolved_at TIMESTAMP(6)  NULL,
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX ix_reports_status ON reports (status, created_at);
