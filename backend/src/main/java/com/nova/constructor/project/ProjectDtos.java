package com.nova.constructor.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Contratos da API de projetos. */
public final class ProjectDtos {

    private ProjectDtos() {}

    public record ProjectSummary(UUID id, String name, String slug, String description, String type, Project.Status status,
                                 int currentVersion, int fileCount, long sizeBytes, JsonNode spec, Instant createdAt,
                                 Instant updatedAt, Instant trashedAt, Project.Visibility visibility,
                                 UUID forkedFromId, String forkedFromLabel) {}

    public record FileEntry(String path, long size) {}

    public record FileContent(String path, long size, boolean binary, String content, int version) {}

    public record VersionSummary(int number, String label, ProjectVersion.Origin origin, int fileCount, long sizeBytes, Instant createdAt) {}

    /** Resultado de uma alteração: a nova versão e os avisos de segredos encontrados. */
    public record ChangeResult(int version, List<SecretScanner.Finding> secretWarnings) {}

    public record Usage(long projects, long storageBytes, long storageLimitBytes) {}

    public record SaveFile(
            @NotBlank(message = "Informe o caminho do arquivo.") @Size(max = 400) String path,
            @NotNull(message = "Informe o conteúdo.") String content,
            @NotNull(message = "Informe a versão base.") Integer baseVersion,
            Boolean createOnly) {}

    public record MoveFile(@NotBlank String from, @NotBlank @Size(max = 400) String to, @NotNull Integer baseVersion) {}

    public record DeletePath(@NotBlank String path, @NotNull Integer baseVersion) {}

    public record Rename(@NotBlank(message = "Dê um nome ao projeto.") @Size(max = 80) String name, @Size(max = 500) String description) {}

    public record LabelVersion(@Size(max = 120) String label) {}

    public record ConfirmDelete(@NotBlank String confirmName) {}
}
