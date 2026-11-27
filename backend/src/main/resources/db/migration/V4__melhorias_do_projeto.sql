-- Aba Melhorias: propostas dispensadas pelo dono e histórico das melhorias aplicadas.
CREATE TABLE improvement_dismissals (
    project_id   CHAR(36)     NOT NULL,
    proposal_id  VARCHAR(80)  NOT NULL,
    dismissed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_improvement_dismissals PRIMARY KEY (project_id, proposal_id),
    CONSTRAINT fk_dismissals_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE improvement_applications (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    project_id     CHAR(36)     NOT NULL,
    proposal_id    VARCHAR(80)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    version_number INT          NOT NULL,
    used_ai        BOOLEAN      NOT NULL,
    applied_by     CHAR(36)     NULL,
    applied_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_improvement_applications PRIMARY KEY (id),
    CONSTRAINT fk_applications_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_user FOREIGN KEY (applied_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX ix_applications_project ON improvement_applications (project_id, applied_at);
