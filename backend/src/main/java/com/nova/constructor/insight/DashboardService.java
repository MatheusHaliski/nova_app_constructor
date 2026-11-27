package com.nova.constructor.insight;

import com.nova.constructor.catalog.Catalog;
import com.nova.constructor.catalog.CatalogService;
import com.nova.constructor.generator.ProjectSpec;
import com.nova.constructor.insight.Model.Insights;
import com.nova.constructor.project.NotificationService;
import com.nova.constructor.project.Project;
import com.nova.constructor.project.ProjectDtos.ProjectSummary;
import com.nova.constructor.project.ProjectService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Painel inicial: continue de onde parou, melhorias pendentes, caminho até publicar e versões novas das tecnologias usadas. */
@Service
public class DashboardService {

    public record RecentProject(UUID id, String name, String visibility, int version, Instant lastActivity, int openProposals, int gaps, int readiness,
                                String nextStep) {}

    public record TechUpdate(UUID projectId, String project, String technology, String current, String available) {}

    public record Dashboard(List<RecentProject> recent, long projects, long unreadNotifications, long openSuggestions, List<TechUpdate> updates) {}

    private final ProjectService projects;
    private final InsightsService insights;
    private final NotificationService notifications;
    private final CatalogService catalog;
    private final JsonMapper mapper;
    private final JdbcClient jdbc;

    public DashboardService(ProjectService projects, InsightsService insights, NotificationService notifications, CatalogService catalog, JsonMapper mapper,
                            JdbcClient jdbc) {
        this.projects = projects;
        this.insights = insights;
        this.notifications = notifications;
        this.catalog = catalog;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    public Dashboard dashboard(UUID userId) {
        List<ProjectSummary> active = projects.list(userId, Project.Status.ACTIVE);
        List<String> recentIds = jdbc.sql("select id from projects where owner_id = ? and status = 'ACTIVE' order by coalesce(last_opened_at, updated_at) desc limit 5")
                .param(userId.toString()).query(String.class).list();
        List<RecentProject> recent = new ArrayList<>();
        for (String id : recentIds) {
            ProjectSummary p = active.stream().filter(x -> x.id().toString().equals(id)).findFirst().orElse(null);
            if (p == null) {
                continue;
            }
            Insights i = insights.insights(userId, p.id(), null);
            List<Model.ProposalView> open = i.proposals().stream().filter(v -> "OPEN".equals(v.state())).toList();
            Model.ReadinessStep next = i.readiness().steps().stream().filter(s -> !s.done()).findFirst().orElse(null);
            recent.add(new RecentProject(p.id(), p.name(), p.visibility().name(), p.currentVersion(), p.updatedAt(), open.size(),
                    (int) open.stream().filter(v -> v.severity() == Model.Severity.GAP).count(), i.readiness().percent(),
                    next == null ? null : next.label() + ": " + next.next()));
        }
        List<TechUpdate> updates = new ArrayList<>();
        Catalog c = catalog.get();
        for (ProjectSummary p : active) {
            ProjectSpec spec;
            try {
                spec = mapper.treeToValue(p.spec(), ProjectSpec.class);
            } catch (RuntimeException e) {
                continue;
            }
            c.backends().stream().filter(b -> b.id().equals(spec.backend().id())).findFirst()
                    .ifPresent(b -> compare(updates, p, b.name(), spec.backend().version(), b.versions()));
            if (spec.frontend() != null) {
                c.frontends().stream().filter(f -> f.id().equals(spec.frontend().id())).findFirst()
                        .ifPresent(f -> compare(updates, p, f.name(), spec.frontend().version(), f.versions()));
            }
        }
        long suggestions = jdbc.sql("select count(*) from project_suggestions s join projects p on p.id = s.project_id where p.owner_id = ? and s.status = 'OPEN'")
                .param(userId.toString()).query(Long.class).single();
        return new Dashboard(recent, active.size(), notifications.unread(userId), suggestions, updates);
    }

    /** Versão nova = a maior da lista do catálogo, quando é diferente (e maior) que a do projeto. */
    private static void compare(List<TechUpdate> out, ProjectSummary p, String tech, String current, List<String> versions) {
        if (current == null || versions == null || versions.isEmpty()) {
            return;
        }
        String newest = versions.stream().max(Comparator.comparing(DashboardService::key)).orElse(current);
        if (key(newest).compareTo(key(current)) > 0) {
            out.add(new TechUpdate(p.id(), p.name(), tech, current, newest));
        }
    }

    /** Chave de ordenação de versões numéricas (ex.: 4.1.2 → 00004.00001.00002). */
    static String key(String version) {
        StringBuilder sb = new StringBuilder();
        for (String part : version.split("[.-]")) {
            String digits = part.replaceAll("\\D", "");
            sb.append(String.format("%05d.", digits.isEmpty() ? 0 : Integer.parseInt(digits.length() > 5 ? digits.substring(0, 5) : digits)));
        }
        return sb.toString();
    }
}
