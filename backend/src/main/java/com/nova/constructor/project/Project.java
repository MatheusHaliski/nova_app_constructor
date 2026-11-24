package com.nova.constructor.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Projeto salvo no perfil do usuário. Nasce privado: só o dono acessa. */
@Entity
@Table(name = "projects")
public class Project {

    public enum Status { ACTIVE, ARCHIVED, TRASHED }

    /**
     * Quem pode ver o projeto. PRIVATE (padrão): só o dono. LINK: quem tem o link, fora do feed e da busca.
     * COMMUNITY: usuários logados da rede NOVA. PUBLIC: qualquer pessoa.
     */
    public enum Visibility {
        PRIVATE, LINK, COMMUNITY, PUBLIC;

        public boolean listed() {
            return this == COMMUNITY || this == PUBLIC;
        }
    }

    /** Licença exibida no projeto; sem licença, terceiros não podem baixar. */
    public enum License { MIT, APACHE_2_0, GPL_3_0, ALL_RIGHTS_RESERVED }

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "owner_id", nullable = false, length = 36)
    private UUID ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 60)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String type;

    /** Especificação do formulário (JSON), usada na ficha técnica e para "gerar igual". */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "spec_json", nullable = false)
    private String specJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "trashed_at")
    private Instant trashedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility = Visibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private License license;

    @Column(name = "published_at")
    private Instant publishedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "forked_from_id", length = 36)
    private UUID forkedFromId;

    /** "@autor/projeto" no momento do fork: continua visível mesmo se o original for excluído. */
    @Column(name = "forked_from_label", length = 140)
    private String forkedFromLabel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {}

    public Project(UUID ownerId, String name, String slug, String description, String type, String specJson) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.type = type;
        this.specJson = specJson;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getSpecJson() { return specJson; }
    public Status getStatus() { return status; }
    public int getCurrentVersion() { return currentVersion; }
    public Instant getTrashedAt() { return trashedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Visibility getVisibility() { return visibility; }
    public License getLicense() { return license; }
    public Instant getPublishedAt() { return publishedAt; }
    public UUID getForkedFromId() { return forkedFromId; }
    public String getForkedFromLabel() { return forkedFromLabel; }

    public void share(Visibility visibility, License license, Instant now) {
        if (visibility.listed() && !this.visibility.listed()) {
            this.publishedAt = now;
        }
        if (!visibility.listed()) {
            this.publishedAt = null;
        }
        this.visibility = visibility;
        this.license = license;
    }

    public void markForkOf(UUID originalId, String label) {
        this.forkedFromId = originalId;
        this.forkedFromLabel = label;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
        this.updatedAt = Instant.now();
    }

    public void setStatus(Status status, Instant now) {
        this.status = status;
        this.trashedAt = status == Status.TRASHED ? now : null;
    }
}
