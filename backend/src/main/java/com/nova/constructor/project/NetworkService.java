package com.nova.constructor.project;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.project.CommunityService.Card;
import com.nova.constructor.project.NotificationService.Kind;
import com.nova.constructor.project.Project.License;
import com.nova.constructor.project.Repositories.ProjectRepository;
import com.nova.constructor.user.ProfileVisibility;
import com.nova.constructor.user.User;
import com.nova.constructor.user.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Rede NOVA além do feed: remixar um projeto, sugerir alterações ao dono, perfil público com portfólio e projetos fixados. */
@Service
public class NetworkService {

    public record SuggestionFile(String path, String content) {}

    public record NewSuggestion(String title, String body, List<SuggestionFile> files) {}

    /** Sugestão de outra pessoa, como o dono a vê. */
    public record Suggestion(long id, UUID projectId, String author, String title, String body, List<SuggestionFile> files, String status, Instant createdAt) {}

    public record Remix(JsonNode spec, String origin, License license) {}

    public record Profile(String name, String username, String bio, String websiteUrl, String githubUsername, Instant memberSince, List<Card> pinned,
                          List<Card> projects, List<String> stacks, long contributions, long starsReceived, boolean mine) {}

    private final ProjectRepository projects;
    private final CommunityService community;
    private final NotificationService notifications;
    private final UserRepository users;
    private final JdbcClient jdbc;
    private final JsonMapper mapper;
    private final Clock clock;

    public NetworkService(ProjectRepository projects, CommunityService community, NotificationService notifications, UserRepository users,
                          JdbcClient jdbc, JsonMapper mapper, Clock clock) {
        this.projects = projects;
        this.community = community;
        this.notifications = notifications;
        this.users = users;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------------- remixar ----------------

    /** Especificação de um projeto da rede para começar um app novo a partir dela (formulário já preenchido). */
    @Transactional(readOnly = true)
    public Remix remix(UUID userId, UUID projectId) {
        Project p = community.load(userId, projectId);
        if (p.getLicense() == License.ALL_RIGHTS_RESERVED && !p.getOwnerId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "O autor reservou todos os direitos; este projeto não pode ser remixado.");
        }
        JsonNode spec = mapper.readTree(p.getSpecJson());
        if (spec instanceof tools.jackson.databind.node.ObjectNode o) {
            String name = "Remix de " + p.getName();
            o.put("name", name.length() > 80 ? name.substring(0, 80) : name);
        }
        return new Remix(spec, "@" + community.username(p.getOwnerId()) + "/" + p.getSlug(), p.getLicense());
    }

    // ---------------- sugestões (revisão de outra pessoa) ----------------

    @Transactional
    public long suggest(UUID userId, UUID projectId, NewSuggestion s) {
        Project p = community.load(userId, projectId);
        if (p.getOwnerId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Este projeto é seu: altere direto nos arquivos.");
        }
        String title = s == null || s.title() == null ? "" : s.title().strip();
        String body = s == null || s.body() == null ? "" : s.body().strip();
        if (title.isEmpty() || title.length() > 120 || body.isEmpty() || body.length() > 2000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Dê um título (até 120 caracteres) e explique a sugestão (até 2000).");
        }
        List<SuggestionFile> files = s.files() == null ? List.of() : s.files();
        if (files.size() > 10) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Uma sugestão altera no máximo 10 arquivos.");
        }
        for (SuggestionFile f : files) {
            String path = PathRules.normalize(f.path());
            if (PathRules.isSecretFile(path) || f.content() == null || f.content().length() > 200_000) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Arquivo não permitido na sugestão: " + f.path());
            }
        }
        jdbc.sql("insert into project_suggestions (project_id, author_id, title, body, files_json, status, created_at) values (?, ?, ?, ?, ?, 'OPEN', ?)")
                .params(projectId.toString(), userId.toString(), title, body, files.isEmpty() ? null : mapper.writeValueAsString(files),
                        Timestamp.from(clock.instant())).update();
        long id = jdbc.sql("select max(id) from project_suggestions where project_id = ? and author_id = ?").params(projectId.toString(), userId.toString())
                .query(Long.class).single();
        notifications.notify(p.getOwnerId(), Kind.SUGGESTION, "@" + community.username(userId) + " sugeriu uma alteração em " + p.getName() + ": " + title,
                "/projetos/" + projectId + "?aba=melhorias");
        return id;
    }

    /** Sugestões abertas para o dono (aparecem na aba Melhorias). */
    @Transactional(readOnly = true)
    public List<Suggestion> open(UUID projectId) {
        return jdbc.sql("select s.id, s.author_id, s.title, s.body, s.files_json, s.status, s.created_at, u.username from project_suggestions s "
                        + "left join users u on u.id = s.author_id where s.project_id = ? and s.status = 'OPEN' order by s.created_at")
                .param(projectId.toString())
                .query((rs, n) -> new Suggestion(rs.getLong("id"), projectId, rs.getString("username"), rs.getString("title"), rs.getString("body"),
                        files(rs.getString("files_json")), rs.getString("status"), rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** Fecha uma sugestão (aplicada ou dispensada) e avisa quem sugeriu. */
    @Transactional
    public void resolve(UUID projectId, long suggestionId, boolean applied, int version) {
        Map<String, Object> row = jdbc.sql("select author_id, title from project_suggestions where id = ? and project_id = ? and status = 'OPEN'")
                .params(suggestionId, projectId.toString()).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sugestão não encontrada."));
        jdbc.sql("update project_suggestions set status = ?, resolved_at = ? where id = ?")
                .params(applied ? "APPLIED" : "DISMISSED", Timestamp.from(clock.instant()), suggestionId).update();
        Project p = projects.findById(projectId).orElseThrow();
        if (applied && row.get("author_id") != null) {
            notifications.notify(UUID.fromString((String) row.get("author_id")), Kind.SUGGESTION,
                    "Sua sugestão \"" + row.get("title") + "\" foi aplicada em " + p.getName() + " (versão " + version + ").", "/comunidade/" + projectId);
        }
    }

    private List<SuggestionFile> files(String json) {
        if (json == null) {
            return List.of();
        }
        return List.of(mapper.readValue(json, SuggestionFile[].class));
    }

    // ---------------- perfil público e portfólio ----------------

    @Transactional(readOnly = true)
    public Profile profile(UUID viewerId, String username) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Perfil não encontrado."));
        boolean mine = user.getId().equals(viewerId);
        if (!mine && user.getProfileVisibility() == ProfileVisibility.PRIVATE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Perfil não encontrado.");
        }
        String owner = user.getId().toString();
        List<Card> listed = new ArrayList<>();
        for (Project p : projects.findByOwnerIdAndStatusOrderByUpdatedAtDesc(user.getId(), Project.Status.ACTIVE)) {
            if (p.getVisibility().listed()) {
                listed.add(community.card(p, viewerId));
            }
        }
        List<String> pinnedIds = jdbc.sql("select project_id from pinned_projects where user_id = ? order by position").param(owner).query(String.class).list();
        List<Card> pinned = new ArrayList<>();
        for (String id : pinnedIds) {
            listed.stream().filter(c -> c.id().toString().equals(id)).findFirst().ifPresent(pinned::add);
        }
        Map<String, Integer> stacks = new LinkedHashMap<>();
        for (Card c : listed) {
            for (String path : List.of("/backend/id", "/frontend/id", "/database/id")) {
                JsonNode n = c.spec() == null ? null : c.spec().at(path);
                if (n != null && !n.isMissingNode() && !n.isNull()) {
                    stacks.merge(n.asString(), 1, Integer::sum);
                }
            }
        }
        long contributions = jdbc.sql("select count(*) from project_suggestions where author_id = ? and status = 'APPLIED'").param(owner).query(Long.class).single();
        long stars = jdbc.sql("select count(*) from project_stars s join projects p on p.id = s.project_id where p.owner_id = ? and s.user_id <> ?")
                .params(owner, owner).query(Long.class).single();
        return new Profile(user.getName(), user.getUsername(), user.getBio(), user.getWebsiteUrl(), user.getGithubUsername(), user.getCreatedAt(), pinned,
                listed, stacks.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).map(Map.Entry::getKey).toList(), contributions, stars, mine);
    }

    /** Projetos fixados no portfólio: até 6, só os seus e publicados na rede. */
    @Transactional
    public List<String> pin(UUID userId, List<UUID> projectIds) {
        if (projectIds == null || projectIds.size() > 6) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Fixe até 6 projetos.");
        }
        for (UUID id : projectIds) {
            Project p = projects.findByIdAndOwnerId(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));
            if (!p.getVisibility().listed() || p.getStatus() != Project.Status.ACTIVE) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Só projetos publicados na rede podem ser fixados: " + p.getName());
            }
        }
        jdbc.sql("delete from pinned_projects where user_id = ?").param(userId.toString()).update();
        int position = 0;
        for (UUID id : projectIds.stream().distinct().toList()) {
            jdbc.sql("insert into pinned_projects (user_id, project_id, position) values (?, ?, ?)").params(userId.toString(), id.toString(), position++).update();
        }
        return projectIds.stream().map(UUID::toString).toList();
    }
}
