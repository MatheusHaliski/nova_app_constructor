package com.nova.constructor.project;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.config.NovaProperties;
import com.nova.constructor.generator.GeneratorService.Generated;
import com.nova.constructor.project.ProjectDtos.ChangeResult;
import com.nova.constructor.project.ProjectDtos.FileContent;
import com.nova.constructor.project.ProjectDtos.FileEntry;
import com.nova.constructor.project.ProjectDtos.ProjectSummary;
import com.nova.constructor.project.ProjectDtos.Usage;
import com.nova.constructor.project.ProjectDtos.VersionSummary;
import com.nova.constructor.project.Repositories.BlobRepository;
import com.nova.constructor.project.Repositories.ProjectRepository;
import com.nova.constructor.project.Repositories.VersionFileRepository;
import com.nova.constructor.project.Repositories.VersionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Projetos salvos: cada alteração cria uma nova versão imutável (o histórico nunca é reescrito), só o dono acessa,
 * e os downloads nunca levam arquivos de segredos.
 */
@Service
public class ProjectService {

    static final int MAX_FILES = 800;
    static final long MAX_FILE_BYTES = 1024 * 1024;
    static final long MAX_PROJECT_BYTES = 20L * 1024 * 1024;

    public record Archive(String fileName, String contentType, byte[] bytes, String sha256) {}

    private final ProjectRepository projects;
    private final VersionRepository versions;
    private final VersionFileRepository versionFiles;
    private final BlobRepository blobs;
    private final JsonMapper mapper;
    private final Clock clock;
    private final long storageLimitBytes;

    public ProjectService(ProjectRepository projects, VersionRepository versions, VersionFileRepository versionFiles,
                          BlobRepository blobs, JsonMapper mapper, Clock clock, NovaProperties props) {
        this.projects = projects;
        this.versions = versions;
        this.versionFiles = versionFiles;
        this.blobs = blobs;
        this.mapper = mapper;
        this.clock = clock;
        this.storageLimitBytes = props.projects().storageLimit().toBytes();
    }

    // ---------------- criação e consulta ----------------

    @Transactional
    public ProjectSummary createFromGenerated(UUID ownerId, Generated generated) {
        var spec = generated.plan().spec();
        Project project = projects.save(new Project(ownerId, spec.name(), generated.plan().slug(), spec.description(),
                spec.type(), mapper.writeValueAsString(spec)));
        Map<String, byte[]> files = new LinkedHashMap<>();
        generated.files().forEach((path, content) -> files.put(path, content.getBytes(StandardCharsets.UTF_8)));
        writeVersion(project, 1, "Estrutura inicial gerada", ProjectVersion.Origin.GENERATOR, ownerId, files);
        return summary(project);
    }

    /** Cria um projeto a partir de arquivos prontos (usado no fork); nasce privado. */
    @Transactional
    public Project createFromFiles(UUID ownerId, String name, String slug, String description, String type, String specJson,
                                   Map<String, byte[]> files, String label, ProjectVersion.Origin origin) {
        Project project = projects.save(new Project(ownerId, name, slug, description, type, specJson));
        writeVersion(project, 1, label, origin, ownerId, files);
        return project;
    }

    /** Arquivos da versão atual que terceiros podem ver: sem os caminhos ocultos e sem arquivos de segredos. */
    @Transactional(readOnly = true)
    public Map<String, byte[]> visibleFiles(Project project, java.util.Collection<String> hiddenPaths) {
        Map<String, byte[]> files = currentFiles(project);
        files.keySet().removeIf(path -> PathRules.isSecretFile(path)
                || hiddenPaths.stream().anyMatch(h -> path.equals(h) || path.startsWith(h + "/")));
        return files;
    }

    public ProjectSummary summaryOf(Project project) {
        return summary(project);
    }

    public Archive archive(Project project, Map<String, byte[]> files) {
        String base = project.getSlug() + "-v" + project.getCurrentVersion();
        byte[] bytes = zip(base, files);
        return new Archive(base + ".zip", "application/zip", bytes, sha256(bytes));
    }

    public static String textOf(byte[] bytes) {
        return decodeText(bytes);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> list(UUID ownerId, Project.Status status) {
        return projects.findByOwnerIdAndStatusOrderByUpdatedAtDesc(ownerId, status).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ProjectSummary get(UUID ownerId, UUID id) {
        return summary(load(ownerId, id));
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID ownerId) {
        return new Usage(projects.countByOwnerIdAndStatusNot(ownerId, Project.Status.TRASHED), blobs.storageUsedBy(ownerId), storageLimitBytes);
    }

    @Transactional(readOnly = true)
    public List<FileEntry> tree(UUID ownerId, UUID id, Integer version) {
        Project project = load(ownerId, id);
        return versionFiles.findByVersionIdOrderByPath(version(project, version).getId()).stream()
                .map(f -> new FileEntry(f.getPath(), f.getSizeBytes())).toList();
    }

    @Transactional(readOnly = true)
    public FileContent file(UUID ownerId, UUID id, String path, Integer version) {
        Project project = load(ownerId, id);
        ProjectVersion v = version(project, version);
        VersionFile file = versionFiles.findByVersionIdAndPath(v.getId(), path)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Arquivo não encontrado: " + path));
        byte[] bytes = blobs.findById(file.getBlobSha()).orElseThrow().getContent();
        String text = decodeText(bytes);
        return new FileContent(path, bytes.length, text == null, text, v.getNumber());
    }

    @Transactional(readOnly = true)
    public List<VersionSummary> versions(UUID ownerId, UUID id) {
        Project project = load(ownerId, id);
        return versions.findByProjectIdOrderByNumberDesc(project.getId()).stream()
                .map(v -> new VersionSummary(v.getNumber(), v.getLabel(), v.getOrigin(), v.getFileCount(), v.getSizeBytes(), v.getCreatedAt()))
                .toList();
    }

    // ---------------- alterações (cada uma gera uma versão) ----------------

    @Transactional
    /**
     * @param createOnly criação de arquivo novo: recusa se o caminho já existir, para nunca sobrescrever sem querer
     */
    public ChangeResult saveFile(UUID ownerId, UUID id, String rawPath, String content, int baseVersion, boolean createOnly) {
        Project project = editable(ownerId, id, baseVersion);
        String path = PathRules.normalize(rawPath);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "Arquivo acima de 1 MB.");
        }
        Map<String, byte[]> files = currentFiles(project);
        boolean existed = files.containsKey(path);
        if (existed && createOnly) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um arquivo em " + path + ". Abra-o para editar.");
        }
        files.put(path, bytes);
        int number = writeVersion(project, baseVersion + 1, (existed ? "Editou " : "Criou ") + path, ProjectVersion.Origin.USER, ownerId, files);
        return new ChangeResult(number, SecretScanner.scan(path, content));
    }

    @Transactional
    public ChangeResult move(UUID ownerId, UUID id, String rawFrom, String rawTo, int baseVersion) {
        Project project = editable(ownerId, id, baseVersion);
        String from = PathRules.normalize(rawFrom);
        String to = PathRules.normalize(rawTo);
        Map<String, byte[]> files = currentFiles(project);
        Map<String, byte[]> next = new LinkedHashMap<>();
        boolean moved = false;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String p = e.getKey();
            if (p.equals(from)) {
                next.put(to, e.getValue());
                moved = true;
            } else if (p.startsWith(from + "/")) {
                next.put(to + p.substring(from.length()), e.getValue());
                moved = true;
            } else {
                next.put(p, e.getValue());
            }
        }
        if (!moved) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Nada encontrado em: " + from);
        }
        if (next.size() != files.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe um arquivo no destino: " + to);
        }
        return new ChangeResult(writeVersion(project, baseVersion + 1, "Moveu " + from + " para " + to, ProjectVersion.Origin.USER, ownerId, next), List.of());
    }

    @Transactional
    public ChangeResult delete(UUID ownerId, UUID id, String rawPath, int baseVersion) {
        Project project = editable(ownerId, id, baseVersion);
        String path = PathRules.normalize(rawPath);
        Map<String, byte[]> files = currentFiles(project);
        int before = files.size();
        files.keySet().removeIf(p -> p.equals(path) || p.startsWith(path + "/"));
        if (files.size() == before) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Nada encontrado em: " + path);
        }
        return new ChangeResult(writeVersion(project, baseVersion + 1, "Excluiu " + path, ProjectVersion.Origin.USER, ownerId, files), List.of());
    }

    /** Restaurar cria uma nova versão igual à escolhida; nada do histórico é apagado. */
    @Transactional
    public ChangeResult restore(UUID ownerId, UUID id, int versionNumber) {
        Project project = load(ownerId, id);
        requireActive(project);
        Map<String, byte[]> files = filesOf(version(project, versionNumber));
        int number = writeVersion(project, project.getCurrentVersion() + 1, "Restaurou a versão " + versionNumber, ProjectVersion.Origin.RESTORE, ownerId, files);
        return new ChangeResult(number, List.of());
    }

    @Transactional
    public VersionSummary labelVersion(UUID ownerId, UUID id, int versionNumber, String label) {
        ProjectVersion v = version(load(ownerId, id), versionNumber);
        v.setLabel(label == null || label.isBlank() ? null : label.trim());
        return new VersionSummary(v.getNumber(), v.getLabel(), v.getOrigin(), v.getFileCount(), v.getSizeBytes(), v.getCreatedAt());
    }

    @Transactional
    public ProjectSummary rename(UUID ownerId, UUID id, String name, String description) {
        Project project = load(ownerId, id);
        project.rename(name.trim(), description == null || description.isBlank() ? null : description.trim());
        return summary(project);
    }

    // ---------------- ciclo de vida ----------------

    @Transactional
    public ProjectSummary setStatus(UUID ownerId, UUID id, Project.Status status) {
        Project project = load(ownerId, id);
        project.setStatus(status, clock.instant());
        return summary(project);
    }

    /** Exclusão definitiva: só da lixeira e digitando o nome do projeto. */
    @Transactional
    public void deletePermanently(UUID ownerId, UUID id, String confirmName) {
        Project project = load(ownerId, id);
        if (project.getStatus() != Project.Status.TRASHED) {
            throw new ApiException(HttpStatus.CONFLICT, "Mova o projeto para a lixeira antes de excluir definitivamente.");
        }
        if (!project.getName().equals(confirmName)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Digite o nome do projeto para confirmar.");
        }
        projects.delete(project);
        projects.flush();
        blobs.deleteOrphans();
    }

    // ---------------- download ----------------

    /** Empacota o projeto (ou uma pasta) em ZIP ou tar.gz. Arquivos .env nunca entram; .env.example entra. */
    @Transactional(readOnly = true)
    public Archive download(UUID ownerId, UUID id, Integer version, String pathPrefix, String format) {
        Project project = load(ownerId, id);
        ProjectVersion v = version(project, version);
        String prefix = pathPrefix == null || pathPrefix.isBlank() ? null : PathRules.normalize(pathPrefix);
        Map<String, byte[]> files = new LinkedHashMap<>();
        filesOf(v).forEach((path, bytes) -> {
            boolean inScope = prefix == null || path.equals(prefix) || path.startsWith(prefix + "/");
            if (inScope && !PathRules.isSecretFile(path)) {
                files.put(path, bytes);
            }
        });
        if (files.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Nada para baixar em: " + pathPrefix);
        }
        boolean tar = "tar.gz".equals(format);
        String base = project.getSlug() + "-v" + v.getNumber();
        byte[] bytes = tar ? tarGz(base, files) : zip(base, files);
        return new Archive(base + (tar ? ".tar.gz" : ".zip"), tar ? "application/gzip" : "application/zip", bytes, sha256(bytes));
    }

    private static byte[] zip(String base, Map<String, byte[]> files) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(base + "/" + e.getKey());
                entry.setUnixMode(PathRules.isExecutable(e.getKey()) ? 0100755 : 0100644);
                entry.setSize(e.getValue().length);
                zip.putArchiveEntry(entry);
                zip.write(e.getValue());
                zip.closeArchiveEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static byte[] tarGz(String base, Map<String, byte[]> files) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                for (Map.Entry<String, byte[]> e : files.entrySet()) {
                    TarArchiveEntry entry = new TarArchiveEntry(base + "/" + e.getKey());
                    entry.setMode(PathRules.isExecutable(e.getKey()) ? 0755 : 0644);
                    entry.setSize(e.getValue().length);
                    tar.putArchiveEntry(entry);
                    tar.write(e.getValue());
                    tar.closeArchiveEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // ---------------- internos ----------------

    private int writeVersion(Project project, int number, String label, ProjectVersion.Origin origin, UUID authorId, Map<String, byte[]> files) {
        if (files.size() > MAX_FILES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "O projeto passou do limite de " + MAX_FILES + " arquivos.");
        }
        long total = files.values().stream().mapToLong(b -> b.length).sum();
        if (total > MAX_PROJECT_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "O projeto passou do limite de 20 MB.");
        }
        // Cota do dono: o que ele já referencia em todas as versões mais o que passa a referenciar nesta gravação.
        // Conteúdo que já existe no servidor por causa de outra conta (fork, arquivo igual) também conta para ele.
        java.util.Set<String> owned = blobs.blobsReferencedBy(project.getOwnerId());
        Map<String, Integer> newToOwner = new LinkedHashMap<>();
        Map<String, byte[]> newBlobs = new LinkedHashMap<>();
        Map<String, String> shaByPath = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String sha = sha256(e.getValue());
            shaByPath.put(e.getKey(), sha);
            if (!owned.contains(sha)) {
                newToOwner.put(sha, e.getValue().length);
            }
            if (!newBlobs.containsKey(sha) && !blobs.existsById(sha)) {
                newBlobs.put(sha, e.getValue());
            }
        }
        long incoming = newToOwner.values().stream().mapToLong(Integer::longValue).sum();
        if (incoming > 0 && blobs.storageUsedBy(project.getOwnerId()) + incoming > storageLimitBytes) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE,
                    "Limite de armazenamento da conta atingido. Exclua definitivamente projetos antigos para liberar espaço.");
        }
        Instant now = clock.instant();
        ProjectVersion version = versions.save(new ProjectVersion(project.getId(), number, cut(label, 120), origin, authorId, files.size(), total, now));
        newBlobs.forEach((sha, bytes) -> blobs.save(new FileBlob(sha, bytes)));
        List<VersionFile> entries = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            entries.add(new VersionFile(version.getId(), e.getKey(), shaByPath.get(e.getKey()), e.getValue().length));
        }
        versionFiles.saveAll(entries);
        project.setCurrentVersion(number);
        return number;
    }

    private Map<String, byte[]> currentFiles(Project project) {
        return filesOf(version(project, null));
    }

    private Map<String, byte[]> filesOf(ProjectVersion version) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (VersionFile f : versionFiles.findByVersionIdOrderByPath(version.getId())) {
            files.put(f.getPath(), blobs.findById(f.getBlobSha()).orElseThrow().getContent());
        }
        return files;
    }

    /** Edição exige projeto ativo e a versão base igual à atual (senão alguém já alterou: conflito). */
    private Project editable(UUID ownerId, UUID id, int baseVersion) {
        Project project = load(ownerId, id);
        requireActive(project);
        if (project.getCurrentVersion() != baseVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "O projeto mudou desde que você abriu (versão atual: "
                    + project.getCurrentVersion() + "). Recarregue antes de salvar.");
        }
        return project;
    }

    private static void requireActive(Project project) {
        if (project.getStatus() != Project.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "Projeto arquivado ou na lixeira é somente leitura.");
        }
    }

    private ProjectVersion version(Project project, Integer number) {
        int n = number == null ? project.getCurrentVersion() : number;
        return versions.findByProjectIdAndNumber(project.getId(), n)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Versão não encontrada: " + n));
    }

    /** Projetos de outras pessoas respondem 404, como se não existissem. */
    private Project load(UUID ownerId, UUID id) {
        return projects.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }

    private ProjectSummary summary(Project p) {
        ProjectVersion v = versions.findByProjectIdAndNumber(p.getId(), p.getCurrentVersion()).orElse(null);
        return new ProjectSummary(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getType(), p.getStatus(),
                p.getCurrentVersion(), v == null ? 0 : v.getFileCount(), v == null ? 0 : v.getSizeBytes(),
                mapper.readTree(p.getSpecJson()), p.getCreatedAt(), p.getUpdatedAt(), p.getTrashedAt(), p.getVisibility(),
                p.getForkedFromId(), p.getForkedFromLabel());
    }

    /** Rótulos automáticos citam caminhos, que podem ser longos; a coluna guarda até 120 caracteres. */
    private static String cut(String label, int max) {
        return label == null || label.length() <= max ? label : label.substring(0, max - 1) + "…";
    }

    private static String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
