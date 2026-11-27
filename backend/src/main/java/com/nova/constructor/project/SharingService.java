package com.nova.constructor.project;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.project.Project.License;
import com.nova.constructor.project.Project.Visibility;
import com.nova.constructor.project.Repositories.ProjectRepository;
import com.nova.constructor.user.User;
import com.nova.constructor.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visibilidade e compartilhamento de um projeto. Todo projeto nasce privado; para ampliar a visibilidade é preciso
 * passar pelas verificações: senha do dono, varredura de segredos nos arquivos visíveis, licença escolhida para
 * entrar na rede e, para tornar público, digitar o nome do projeto.
 */
@Service
public class SharingService {

    public record LinkView(UUID id, String label, Instant createdAt, Instant expiresAt, Instant lastUsedAt) {}

    public record CreatedLink(UUID id, String token, Instant expiresAt) {}

    public record SharingState(Visibility visibility, License license, List<String> hiddenPaths, List<LinkView> links, Instant publishedAt) {}

    public record SharingUpdate(Visibility visibility, License license, List<String> hiddenPaths, String currentPassword, String confirmName) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcClient jdbc;
    private final Clock clock;

    public SharingService(ProjectRepository projects, ProjectService projectService, UserRepository users,
                          PasswordEncoder passwordEncoder, JdbcClient jdbc, Clock clock) {
        this.projects = projects;
        this.projectService = projectService;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SharingState get(UUID ownerId, UUID projectId) {
        return state(load(ownerId, projectId));
    }

    @Transactional
    public SharingState update(UUID ownerId, UUID projectId, SharingUpdate update) {
        Project project = load(ownerId, projectId);
        if (update.visibility() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Escolha a visibilidade.");
        }
        Set<String> hidden = new LinkedHashSet<>();
        if (update.hiddenPaths() != null) {
            for (String raw : update.hiddenPaths()) {
                if (raw != null && !raw.isBlank()) {
                    hidden.add(PathRules.normalize(raw));
                }
            }
        }
        Visibility target = update.visibility();
        boolean widening = target.ordinal() > project.getVisibility().ordinal();
        if (target.listed() && update.license() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Escolha uma licença (ou “Todos os direitos reservados”) antes de publicar na rede.");
        }
        if (widening) {
            User owner = users.findById(ownerId).orElseThrow();
            if (update.currentPassword() == null || !passwordEncoder.matches(update.currentPassword(), owner.getPasswordHash())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Confirme com sua senha atual para ampliar a visibilidade.");
            }
            if (target == Visibility.PUBLIC && !project.getName().equals(update.confirmName())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Para tornar público, digite o nome do projeto.");
            }
        }
        if (target != Visibility.PRIVATE) {
            if (project.getStatus() != Project.Status.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, "Projeto arquivado ou na lixeira não pode ser compartilhado.");
            }
            // A varredura vale sempre que o projeto fica (ou continua) visível: arquivos ocultos mudam o que é exposto.
            List<SecretScanner.Finding> findings = new ArrayList<>();
            projectService.visibleFiles(project, hidden).forEach((path, bytes) -> {
                String text = ProjectService.textOf(bytes);
                if (text != null) {
                    findings.addAll(SecretScanner.scan(path, text));
                }
            });
            if (!findings.isEmpty()) {
                String list = findings.stream().limit(5).map(f -> f.path() + ":" + f.line() + " (" + f.kind() + ")").reduce((a, b) -> a + ", " + b).orElse("");
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Há possíveis credenciais em arquivos que ficariam visíveis: " + list
                        + ". Remova o valor, troque a chave se ela já foi usada, ou oculte o arquivo.");
            }
        }
        Instant now = clock.instant();
        if (project.getVisibility().listed() && !target.listed()) {
            // Sai da rede: estrelas de quem perde o acesso são removidas; forks já feitos continuam.
            jdbc.sql("delete from project_stars where project_id = ? and user_id <> ?").params(projectId.toString(), ownerId.toString()).update();
        }
        if (target == Visibility.PRIVATE) {
            jdbc.sql("update share_links set revoked_at = ? where project_id = ? and revoked_at is null").params(Timestamp.from(now), projectId.toString()).update();
        }
        project.share(target, update.license(), now);
        jdbc.sql("delete from project_hidden_paths where project_id = ?").param(projectId.toString()).update();
        for (String path : hidden) {
            jdbc.sql("insert into project_hidden_paths (project_id, path) values (?, ?)").params(projectId.toString(), path).update();
        }
        return state(project);
    }

    /** Cria um link de compartilhamento; o token só aparece nesta resposta. */
    @Transactional
    public CreatedLink createLink(UUID ownerId, UUID projectId, Integer expiresInDays, String label) {
        String name = label == null || label.isBlank() ? null : label.strip();
        if (name != null && name.length() > 60) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "O nome do link vai até 60 caracteres.");
        }
        Project project = load(ownerId, projectId);
        if (project.getVisibility() == Visibility.PRIVATE) {
            throw new ApiException(HttpStatus.CONFLICT, "Mude a visibilidade para “Link” (ou mais ampla) antes de criar um link.");
        }
        if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > 365)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "A validade do link vai de 1 a 365 dias.");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expires = expiresInDays == null ? null : now.plus(Duration.ofDays(expiresInDays));
        jdbc.sql("insert into share_links (id, project_id, token_hash, expires_at, created_at, label) values (?, ?, ?, ?, ?, ?)")
                .params(id.toString(), projectId.toString(), hash(token), expires == null ? null : Timestamp.from(expires), Timestamp.from(now), name).update();
        return new CreatedLink(id, token, expires);
    }

    @Transactional
    public void revokeLink(UUID ownerId, UUID projectId, UUID linkId) {
        load(ownerId, projectId);
        jdbc.sql("update share_links set revoked_at = ? where id = ? and project_id = ? and revoked_at is null")
                .params(Timestamp.from(clock.instant()), linkId.toString(), projectId.toString()).update();
    }

    /** Projeto de um link válido (não revogado, não expirado, projeto ativo e não privado); senão, 404. */
    @Transactional
    public Project resolveLink(String token) {
        Instant now = clock.instant();
        List<Map<String, Object>> rows = jdbc.sql("select id, project_id, expires_at from share_links where token_hash = ? and revoked_at is null")
                .param(hash(token)).query().listOfRows();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.getFirst();
            Timestamp expires = (Timestamp) row.get("expires_at");
            if (expires == null || expires.toInstant().isAfter(now)) {
                Project project = projects.findById(UUID.fromString((String) row.get("project_id"))).orElse(null);
                if (project != null && project.getStatus() == Project.Status.ACTIVE && project.getVisibility() != Visibility.PRIVATE) {
                    jdbc.sql("update share_links set last_used_at = ? where id = ?").params(Timestamp.from(now), row.get("id")).update();
                    return project;
                }
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "Link inválido, expirado ou revogado.");
    }

    public List<String> hiddenPaths(UUID projectId) {
        return jdbc.sql("select path from project_hidden_paths where project_id = ? order by path").param(projectId.toString()).query(String.class).list();
    }

    private SharingState state(Project project) {
        List<LinkView> links = jdbc.sql("select id, label, created_at, expires_at, last_used_at from share_links where project_id = ? and revoked_at is null order by created_at desc")
                .param(project.getId().toString())
                .query((rs, n) -> new LinkView(UUID.fromString(rs.getString("id")), rs.getString("label"), instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("expires_at")), instant(rs.getTimestamp("last_used_at"))))
                .list();
        return new SharingState(project.getVisibility(), project.getLicense(), hiddenPaths(project.getId()), links, project.getPublishedAt());
    }

    private Project load(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwnerId(projectId, ownerId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
