package com.nova.constructor.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um arquivo dentro de uma versão: caminho + referência ao conteúdo. */
@Entity
@Table(name = "version_files")
public class VersionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(nullable = false, length = 400)
    private String path;

    @Column(name = "blob_sha", nullable = false, length = 64)
    private String blobSha;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected VersionFile() {}

    public VersionFile(Long versionId, String path, String blobSha, long sizeBytes) {
        this.versionId = versionId;
        this.path = path;
        this.blobSha = blobSha;
        this.sizeBytes = sizeBytes;
    }

    public String getPath() { return path; }
    public String getBlobSha() { return blobSha; }
    public long getSizeBytes() { return sizeBytes; }
}
