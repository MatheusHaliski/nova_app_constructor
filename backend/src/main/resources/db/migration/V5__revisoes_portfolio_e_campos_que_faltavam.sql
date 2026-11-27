-- Revisão de outra pessoa: sugestões de alteração enviadas pela rede ao dono do projeto.
CREATE TABLE project_suggestions (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    project_id  CHAR(36)      NOT NULL,
    author_id   CHAR(36)      NULL,
    title       VARCHAR(120)  NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    files_json  LONGTEXT      NULL,
    status      VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMP(6)  NOT NULL,
    resolved_at TIMESTAMP(6)  NULL,
    CONSTRAINT pk_project_suggestions PRIMARY KEY (id),
    CONSTRAINT fk_suggestions_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_suggestions_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX ix_suggestions_project ON project_suggestions (project_id, status, created_at);

-- Perfil público: projetos fixados no portfólio.
CREATE TABLE pinned_projects (
    user_id    CHAR(36) NOT NULL,
    project_id CHAR(36) NOT NULL,
    position   INT      NOT NULL,
    CONSTRAINT pk_pinned_projects PRIMARY KEY (user_id, project_id),
    CONSTRAINT fk_pinned_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_pinned_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

-- Campos que faltavam nas entidades.
-- users: último acesso e última troca de senha (segurança da conta) e links do portfólio.
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP(6) NULL;
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMP(6) NULL;
ALTER TABLE users ADD COLUMN website_url VARCHAR(200) NULL;
ALTER TABLE users ADD COLUMN github_username VARCHAR(39) NULL;
-- refresh_tokens: dispositivo e último uso, para listar e encerrar sessões.
ALTER TABLE refresh_tokens ADD COLUMN user_agent VARCHAR(200) NULL;
ALTER TABLE refresh_tokens ADD COLUMN last_used_at TIMESTAMP(6) NULL;
-- consent_events: versão da política e canal, para provar como o consentimento foi dado (LGPD, art. 8º, § 2º).
ALTER TABLE consent_events ADD COLUMN policy_version VARCHAR(20) NULL;
ALTER TABLE consent_events ADD COLUMN channel VARCHAR(20) NULL;
-- projects: quando o dono abriu o projeto pela última vez (painel "continue de onde parou").
ALTER TABLE projects ADD COLUMN last_opened_at TIMESTAMP(6) NULL;
-- share_links: nome do link, para saber com quem cada um foi compartilhado.
ALTER TABLE share_links ADD COLUMN label VARCHAR(60) NULL;
