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
