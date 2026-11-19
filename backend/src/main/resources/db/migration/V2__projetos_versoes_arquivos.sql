-- Projetos salvos no perfil, com versões e arquivos. O conteúdo é guardado uma vez por hash (file_blobs).
CREATE TABLE projects (
    id              CHAR(36)     NOT NULL,
    owner_id        CHAR(36)     NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    slug            VARCHAR(60)  NOT NULL,
    description     VARCHAR(500),
    type            VARCHAR(20)  NOT NULL,
    spec_json       LONGTEXT     NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    current_version INT          NOT NULL,
    trashed_at      TIMESTAMP(6) NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_projects_owner ON projects (owner_id, status, updated_at);

CREATE TABLE project_versions (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    project_id CHAR(36)     NOT NULL,
    number     INT          NOT NULL,
    label      VARCHAR(120),
    origin     VARCHAR(20)  NOT NULL,
    author_id  CHAR(36)     NOT NULL,
    file_count INT          NOT NULL,
    size_bytes BIGINT       NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_project_versions PRIMARY KEY (id),
    CONSTRAINT uk_project_versions UNIQUE (project_id, number),
    CONSTRAINT fk_project_versions_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE file_blobs (
    sha256     VARCHAR(64) NOT NULL,
    size_bytes BIGINT      NOT NULL,
    content    LONGBLOB    NOT NULL,
    CONSTRAINT pk_file_blobs PRIMARY KEY (sha256)
);

CREATE TABLE version_files (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    version_id BIGINT       NOT NULL,
    path       VARCHAR(400) NOT NULL,
    blob_sha   VARCHAR(64)  NOT NULL,
    size_bytes BIGINT       NOT NULL,
    CONSTRAINT pk_version_files PRIMARY KEY (id),
    CONSTRAINT uk_version_files UNIQUE (version_id, path),
    CONSTRAINT fk_version_files_version FOREIGN KEY (version_id) REFERENCES project_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_version_files_blob FOREIGN KEY (blob_sha) REFERENCES file_blobs (sha256)
);
