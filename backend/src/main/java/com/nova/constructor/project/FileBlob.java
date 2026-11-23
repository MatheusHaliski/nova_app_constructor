package com.nova.constructor.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** Conteúdo de arquivo guardado uma única vez, identificado pelo SHA-256 (versões iguais não ocupam espaço extra). */
@Entity
@Table(name = "file_blobs")
public class FileBlob {

    @Id
    @Column(length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    protected FileBlob() {}

    public FileBlob(String sha256, byte[] content) {
        this.sha256 = sha256;
        this.sizeBytes = content.length;
        this.content = content;
    }

    public String getSha256() { return sha256; }
    public byte[] getContent() { return content; }
}
