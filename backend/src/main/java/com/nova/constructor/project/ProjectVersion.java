package com.nova.constructor.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Versão imutável de um projeto: um retrato de todos os arquivos naquele momento. */
@Entity
@Table(name = "project_versions")
public class ProjectVersion {

    public enum Origin { GENERATOR, USER, RESTORE, FORK, IMPROVEMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "project_id", nullable = false, length = 36)
    private UUID projectId;

    @Column(nullable = false)
    private int number;

    @Column(length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origin origin;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "author_id", nullable = false, length = 36)
    private UUID authorId;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectVersion() {}

    public ProjectVersion(UUID projectId, int number, String label, Origin origin, UUID authorId, int fileCount, long sizeBytes, Instant createdAt) {
        this.projectId = projectId;
        this.number = number;
        this.label = label;
        this.origin = origin;
        this.authorId = authorId;
        this.fileCount = fileCount;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public int getNumber() { return number; }
    public String getLabel() { return label; }
    public Origin getOrigin() { return origin; }
    public int getFileCount() { return fileCount; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setLabel(String label) { this.label = label; }
}
