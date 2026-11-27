package com.nova.constructor.project;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.project.NotificationService.Kind;
import com.nova.constructor.project.Project.License;
import com.nova.constructor.project.Project.Visibility;
import com.nova.constructor.project.ProjectDtos.FileContent;
import com.nova.constructor.project.ProjectDtos.FileEntry;
import com.nova.constructor.project.ProjectDtos.ProjectSummary;
import com.nova.constructor.project.Repositories.ProjectRepository;
import com.nova.constructor.user.User;
import com.nova.constructor.user.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Rede NOVA: o que outras pessoas podem ver e fazer com projetos compartilhados. Só entram projetos ativos com
 * visibilidade Comunidade ou Público; arquivos ocultos pelo dono e arquivos de segredos nunca saem daqui.
 */
@Service
public class CommunityService {

    public record Card(UUID id, String name, String slug, String description, String type, JsonNode spec, Visibility visibility,
                       License license, String author, long stars, long forks, long comments, boolean starredByMe, boolean mine,
                       int fileCount, long sizeBytes, int version, Instant publishedAt, Instant updatedAt,
                       UUID forkedFromId, String forkedFromLabel) {}

    public record Comment(long id, String author, String body, Instant createdAt, boolean mine, boolean canDelete) {}

    public enum ReportReason { SPAM, MALICIOUS_CODE, EXPOSED_SECRET, COPYRIGHT, OFFENSIVE, OTHER }

    public enum ReportAction { DISMISS, UNPUBLISH, HIDE_COMMENT }

    public record Report(long id, UUID projectId, String projectName, String projectAuthor, Long commentId, String commentBody,
                         ReportReason reason, String details, String reporter, String status, String resolution,
                         Instant createdAt, Instant resolvedAt) {}

    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final SharingService sharing;
    private final NotificationService notifications;
    private final UserRepository users;
    private final JdbcClient jdbc;
    private final Clock clock;

    public CommunityService(ProjectRepository projects, ProjectService projectService, SharingService sharing,
                            NotificationService notifications, UserRepository users, JdbcClient jdbc, Clock clock) {
        this.projects = projects;
        this.projectService = projectService;
        this.sharing = sharing;
        this.notifications = notifications;
        this.users = users;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ---------------- descoberta ----------------

    @Transactional(readOnly = true)
    public List<Card> explore(UUID userId, String q, String tech, int page) {
        String text = q == null ? "" : q.replaceAll("[%_\\\\]", " ").trim().toLowerCase();
        String techId = tech == null ? "" : tech.replaceAll("[^A-Za-z0-9.+#-]", "");
        return projects.findListed(text.isEmpty() ? null : "%" + text + "%", techId.isEmpty() ? null : "%\"" + techId + "\"%",
                PageRequest.of(Math.max(page, 0), 24)).stream().map(p -> card(p, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<Card> starred(UUID userId) {
        List<Card> cards = new ArrayList<>();
        for (String id : jdbc.sql("select project_id from project_stars where user_id = ? order by created_at desc").param(userId.toString()).query(String.class).list()) {
            projects.findById(UUID.fromString(id)).filter(p -> readable(p, userId)).ifPresent(p -> cards.add(card(p, userId)));
        }
        return cards;
    }

    @Transactional(readOnly = true)
    public Card detail(UUID userId, UUID id) {
        return card(load(userId, id), userId);
    }

    @Transactional(readOnly = true)
    public List<FileEntry> tree(UUID userId, UUID id) {
        return treeOf(load(userId, id));
    }

    @Transactional(readOnly = true)
    public FileContent file(UUID userId, UUID id, String path) {
        return fileOf(load(userId, id), path);
    }

    @Transactional(readOnly = true)
    public ProjectService.Archive download(UUID userId, UUID id) {
        Project project = load(userId, id);
        return projectService.archive(project, visible(project));
    }

    // ---------------- link de compartilhamento (sem login) ----------------

    @Transactional
    public Card sharedDetail(String token) {
        return card(sharing.resolveLink(token), null);
    }

    @Transactional
    public List<FileEntry> sharedTree(String token) {
        return treeOf(sharing.resolveLink(token));
    }

    @Transactional
    public FileContent sharedFile(String token, String path) {
        return fileOf(sharing.resolveLink(token), path);
    }

    @Transactional
    public ProjectService.Archive sharedDownload(String token) {
        Project project = sharing.resolveLink(token);
        return projectService.archive(project, visible(project));
    }

    // ---------------- estrelas e fork ----------------

    @Transactional
    public Card star(UUID userId, UUID id, boolean on) {
        Project project = load(userId, id);
        boolean has = starredBy(id, userId);
        if (on && !has) {
            jdbc.sql("insert into project_stars (project_id, user_id, created_at) values (?, ?, ?)")
                    .params(id.toString(), userId.toString(), Timestamp.from(clock.instant())).update();
            if (!project.getOwnerId().equals(userId)) {
                notifications.notify(project.getOwnerId(), Kind.STAR, "@" + username(userId) + " favoritou " + project.getName() + ".", "/comunidade/" + id);
            }
        } else if (!on && has) {
            jdbc.sql("delete from project_stars where project_id = ? and user_id = ?").params(id.toString(), userId.toString()).update();
        }
        return card(project, userId);
    }

    /** Copia o que é visível para um projeto novo e privado de quem fez o fork, com a origem registrada. */
    @Transactional
    public ProjectSummary fork(UUID userId, UUID id) {
        Project original = load(userId, id);
        if (original.getOwnerId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Este projeto já é seu.");
        }
        if (original.getLicense() == License.ALL_RIGHTS_RESERVED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "O autor reservou todos os direitos; este projeto não permite fork.");
        }
        String author = username(original.getOwnerId());
        Project fork = projectService.createFromFiles(userId, original.getName(), original.getSlug(), original.getDescription(),
                original.getType(), original.getSpecJson(), visible(original), "Fork de @" + author + "/" + original.getSlug(),
                ProjectVersion.Origin.FORK);
        fork.markForkOf(original.getId(), "@" + author + "/" + original.getSlug());
        notifications.notify(original.getOwnerId(), Kind.FORK, "@" + username(userId) + " fez um fork de " + original.getName() + ".", "/comunidade/" + id);
        return projectService.summaryOf(fork);
    }

    // ---------------- comentários ----------------

    @Transactional(readOnly = true)
    public List<Comment> comments(UUID userId, UUID id, boolean admin) {
        Project project = load(userId, id);
        boolean owner = project.getOwnerId().equals(userId);
        return jdbc.sql("select c.id, c.author_id, c.body, c.created_at, u.username from project_comments c left join users u on u.id = c.author_id "
                        + "where c.project_id = ? and c.hidden = false order by c.created_at, c.id")
                .param(id.toString())
                .query((rs, n) -> {
                    boolean mine = userId.toString().equals(rs.getString("author_id"));
                    return new Comment(rs.getLong("id"), rs.getString("username"), rs.getString("body"),
                            rs.getTimestamp("created_at").toInstant(), mine, mine || owner || admin);
                }).list();
    }

    @Transactional
    public void comment(UUID userId, UUID id, String body) {
        Project project = load(userId, id);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > 2000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "O comentário precisa ter de 1 a 2000 caracteres.");
        }
        jdbc.sql("insert into project_comments (project_id, author_id, body, hidden, created_at) values (?, ?, ?, false, ?)")
                .params(id.toString(), userId.toString(), text, Timestamp.from(clock.instant())).update();
        if (!project.getOwnerId().equals(userId)) {
            notifications.notify(project.getOwnerId(), Kind.COMMENT, "@" + username(userId) + " comentou em " + project.getName() + ".", "/comunidade/" + id);
        }
    }

    /** O autor do comentário, o dono do projeto ou um administrador podem remover. */
    @Transactional
    public void deleteComment(UUID userId, UUID id, long commentId, boolean admin) {
        Project project = load(userId, id);
        // O autor pode ser nulo (conta excluída): a existência do comentário é conferida à parte.
        Map<String, Object> row = jdbc.sql("select author_id from project_comments where id = ? and project_id = ?").params(commentId, id.toString())
                .query().listOfRows().stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comentário não encontrado."));
        String author = (String) row.get("author_id");
        if (!admin && !project.getOwnerId().equals(userId) && !userId.toString().equals(author)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não pode remover este comentário.");
        }
        jdbc.sql("delete from project_comments where id = ?").param(commentId).update();
    }

    // ---------------- denúncias e moderação ----------------

    @Transactional
    public void report(UUID userId, UUID id, Long commentId, ReportReason reason, String details) {
        load(userId, id);
        if (reason == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Escolha o motivo da denúncia.");
        }
        if (commentId != null && jdbc.sql("select count(*) from project_comments where id = ? and project_id = ?").params(commentId, id.toString()).query(Long.class).single() == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Comentário não encontrado.");
        }
        String text = details == null || details.isBlank() ? null : details.strip();
        if (text != null && text.length() > 1000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Os detalhes vão até 1000 caracteres.");
        }
        jdbc.sql("insert into reports (reporter_id, project_id, comment_id, reason, details, status, created_at) values (?, ?, ?, ?, ?, 'OPEN', ?)")
                .params(userId.toString(), id.toString(), commentId, reason.name(), text, Timestamp.from(clock.instant())).update();
    }

    @Transactional(readOnly = true)
    public List<Report> reports(String status) {
        return jdbc.sql("select r.*, p.name as project_name, pu.username as project_author, ru.username as reporter, c.body as comment_body "
                        + "from reports r join projects p on p.id = r.project_id join users pu on pu.id = p.owner_id "
                        + "left join users ru on ru.id = r.reporter_id left join project_comments c on c.id = r.comment_id "
                        + "where r.status = ? order by r.created_at desc limit 200")
                .param("RESOLVED".equals(status) ? "RESOLVED" : "OPEN")
                .query((rs, n) -> new Report(rs.getLong("id"), UUID.fromString(rs.getString("project_id")), rs.getString("project_name"),
                        rs.getString("project_author"), (Long) rs.getObject("comment_id", Long.class), rs.getString("comment_body"),
                        ReportReason.valueOf(rs.getString("reason")), rs.getString("details"), rs.getString("reporter"), rs.getString("status"),
                        rs.getString("resolution"), rs.getTimestamp("created_at").toInstant(), SharingService.instant(rs.getTimestamp("resolved_at"))))
                .list();
    }

    /** Resolve uma denúncia: descartar, despublicar o projeto (volta a privado) ou ocultar o comentário. O dono é avisado. */
    @Transactional
    public void resolve(UUID adminId, long reportId, ReportAction action, String note) {
        Map<String, Object> row = jdbc.sql("select project_id, comment_id, status from reports where id = ?").param(reportId).query().listOfRows()
                .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Denúncia não encontrada."));
        if (!"OPEN".equals(row.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Esta denúncia já foi resolvida.");
        }
        if (action == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Escolha a ação.");
        }
        String projectId = (String) row.get("project_id");
        Number commentId = (Number) row.get("comment_id");
        Project project = projects.findById(UUID.fromString(projectId)).orElseThrow();
        Instant now = clock.instant();
        String reason = note == null || note.isBlank() ? "" : " Motivo: " + note.strip();
        switch (action) {
            case UNPUBLISH -> {
                project.share(Visibility.PRIVATE, project.getLicense(), now);
                jdbc.sql("update share_links set revoked_at = ? where project_id = ? and revoked_at is null").params(Timestamp.from(now), projectId).update();
                jdbc.sql("delete from project_stars where project_id = ? and user_id <> ?").params(projectId, project.getOwnerId().toString()).update();
                notifications.notify(project.getOwnerId(), Kind.MODERATION,
                        "A moderação tornou " + project.getName() + " privado após uma denúncia." + reason, "/projetos/" + projectId);
            }
            case HIDE_COMMENT -> {
                if (commentId == null) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Esta denúncia não é sobre um comentário.");
                }
                String author = jdbc.sql("select author_id from project_comments where id = ?").param(commentId.longValue()).query(String.class).optional().orElse(null);
                jdbc.sql("update project_comments set hidden = true where id = ?").param(commentId.longValue()).update();
                if (author != null) {
                    notifications.notify(UUID.fromString(author), Kind.MODERATION,
                            "A moderação ocultou um comentário seu em " + project.getName() + "." + reason, null);
                }
            }
            case DISMISS -> { }
        }
        String resolution = action.name() + (note == null || note.isBlank() ? "" : ": " + note.strip());
        jdbc.sql("update reports set status = 'RESOLVED', resolution = ?, resolved_by = ?, resolved_at = ? where id = ?")
                .params(resolution.length() > 500 ? resolution.substring(0, 500) : resolution, adminId.toString(), Timestamp.from(now), reportId).update();
    }

    // ---------------- apoio ----------------

    /** Listado na rede (ou do próprio usuário); o resto responde 404, como se não existisse. */
    Project load(UUID userId, UUID id) {
        return projects.findById(id).filter(p -> readable(p, userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
    }

    private static boolean readable(Project p, UUID userId) {
        return p.getStatus() == Project.Status.ACTIVE && (p.getVisibility().listed() || p.getOwnerId().equals(userId));
    }

    private Map<String, byte[]> visible(Project project) {
        return projectService.visibleFiles(project, sharing.hiddenPaths(project.getId()));
    }

    private List<FileEntry> treeOf(Project project) {
        return visible(project).entrySet().stream().map(e -> new FileEntry(e.getKey(), e.getValue().length)).toList();
    }

    private FileContent fileOf(Project project, String rawPath) {
        String path = PathRules.normalize(rawPath);
        byte[] bytes = visible(project).get(path);
        if (bytes == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Arquivo não encontrado: " + path);
        }
        String text = ProjectService.textOf(bytes);
        return new FileContent(path, bytes.length, text == null, text, project.getCurrentVersion());
    }

    Card card(Project p, UUID userId) {
        String pid = p.getId().toString();
        Map<String, byte[]> files = visible(p);
        long size = files.values().stream().mapToLong(b -> b.length).sum();
        ProjectSummary s = projectService.summaryOf(p);
        return new Card(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getType(), s.spec(), p.getVisibility(), p.getLicense(),
                username(p.getOwnerId()), count("select count(*) from project_stars where project_id = ?", pid),
                projects.countByForkedFromId(p.getId()), count("select count(*) from project_comments where project_id = ? and hidden = false", pid),
                userId != null && starredBy(p.getId(), userId), p.getOwnerId().equals(userId), files.size(), size, p.getCurrentVersion(),
                p.getPublishedAt(), p.getUpdatedAt(), p.getForkedFromId(), p.getForkedFromLabel());
    }

    private long count(String sql, String projectId) {
        return jdbc.sql(sql).param(projectId).query(Long.class).single();
    }

    private boolean starredBy(UUID projectId, UUID userId) {
        return jdbc.sql("select count(*) from project_stars where project_id = ? and user_id = ?").params(projectId.toString(), userId.toString()).query(Long.class).single() > 0;
    }

    String username(UUID userId) {
        return users.findById(userId).map(User::getUsername).orElse("usuário removido");
    }
}
