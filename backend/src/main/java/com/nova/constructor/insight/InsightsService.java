package com.nova.constructor.insight;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.config.NovaProperties;
import com.nova.constructor.generator.ProjectSpec;
import com.nova.constructor.insight.Analyzer.Context;
import com.nova.constructor.insight.Model.Analysis;
import com.nova.constructor.insight.Model.Applied;
import com.nova.constructor.insight.Model.ApplyResult;
import com.nova.constructor.insight.Model.ChangeAction;
import com.nova.constructor.insight.Model.ChangeSet;
import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.Field;
import com.nova.constructor.insight.Model.FileChange;
import com.nova.constructor.insight.Model.Insights;
import com.nova.constructor.insight.Model.Mock;
import com.nova.constructor.insight.Model.Operation;
import com.nova.constructor.insight.Model.Preview;
import com.nova.constructor.insight.Model.PreviewBundle;
import com.nova.constructor.insight.Model.PreviewPage;
import com.nova.constructor.insight.Model.Proposal;
import com.nova.constructor.insight.Model.ProposalView;
import com.nova.constructor.insight.Model.Resource;
import com.nova.constructor.project.ProjectService;
import com.nova.constructor.project.ProjectService.Snapshot;
import com.nova.constructor.project.ProjectVersion;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Análise do projeto e aba Melhorias. A análise roda sobre os arquivos de uma versão (com as edições manuais) e fica em
 * cache por versão; dispensas e o histórico de melhorias aplicadas ficam no banco.
 */
@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);
    private static final int MAX_TEXT = 1024 * 1024;
    private static final int MAX_BUNDLE = 1536 * 1024;
    private static final int MAX_AI_CONTEXT = 120 * 1024;
    private static final int CACHE_SIZE = 32;

    /** Pedido de prévia ou aplicação. {@code files}: na aplicação com IA, o resultado que a pessoa viu na prévia. */
    public record ImprovementRequest(Integer baseVersion, Map<String, Object> params, Boolean useAi, String note, List<FileIn> files) {}

    public record FileIn(String path, String content) {}

    private record Entry(Context context, Analysis analysis) {}

    private final ProjectService projects;
    private final Analyzer analyzer;
    private final Improvements improvements;
    private final JsonMapper mapper;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ImprovementAi ai;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public InsightsService(ProjectService projects, Analyzer analyzer, Improvements improvements, JsonMapper mapper, JdbcClient jdbc, Clock clock,
                           NovaProperties props) {
        this.projects = projects;
        this.analyzer = analyzer;
        this.improvements = improvements;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.clock = clock;
        String key = props.ai().anthropicApiKey();
        this.ai = key == null || key.isBlank() ? null : new ClaudeImprovementAi(key, props.ai().model(), mapper);
    }

    public boolean aiAvailable() {
        return ai != null;
    }

    @Transactional(readOnly = true)
    public Insights insights(UUID ownerId, UUID projectId, Integer version) {
        Snapshot snap = projects.snapshot(ownerId, projectId, version);
        Analysis a = analyzed(snap).analysis();
        Set<String> dismissed = dismissed(projectId);
        List<ProposalView> views = a.proposals().stream().map(p -> ProposalView.of(p, ai != null, dismissed.contains(p.id()))).toList();
        return new Insights(snap.version(), clock.instant(), a.stack(), a.stages(), a.layers(), a.resources(), a.endpoints(), a.entities(), a.tables(),
                a.screens(), a.dependencies(), views, applied(projectId), ai != null, a.fileCount(), a.testCount());
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID ownerId, UUID projectId, String proposalId, ImprovementRequest req) {
        Snapshot snap = current(ownerId, projectId, req);
        ChangeSet set = changeSet(snap, proposalId, req, false);
        return new Preview(snap.version(), snap.version() + 1, set);
    }

    @Transactional
    public ApplyResult apply(UUID ownerId, UUID projectId, String proposalId, ImprovementRequest req) {
        Snapshot snap = current(ownerId, projectId, req);
        Proposal p = proposal(snap, proposalId);
        ChangeSet set = changeSet(snap, proposalId, req, true);
        Map<String, String> upserts = new LinkedHashMap<>();
        set.changes().forEach(c -> upserts.put(c.path(), c.after()));
        int version = projects.applyChanges(ownerId, projectId, snap.version(), upserts, "Melhoria: " + p.title(), ProjectVersion.Origin.IMPROVEMENT)
                .version();
        jdbc.sql("insert into improvement_applications (project_id, proposal_id, title, version_number, used_ai, applied_by, applied_at) values (?, ?, ?, ?, ?, ?, ?)")
                .params(projectId.toString(), proposalId, cut(p.title(), 200), version, set.usedAi(), ownerId.toString(), Timestamp.from(clock.instant()))
                .update();
        return new ApplyResult(version, set.changes().stream().map(FileChange::path).toList(), set.notes(), set.usedAi());
    }

    @Transactional
    public void dismiss(UUID ownerId, UUID projectId, String proposalId, boolean dismissed) {
        projects.snapshot(ownerId, projectId, null);
        if (!proposalId.matches("[a-z-]{2,20}-[0-9a-f]{10}")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Proposta não encontrada.");
        }
        jdbc.sql("delete from improvement_dismissals where project_id = ? and proposal_id = ?").params(projectId.toString(), proposalId).update();
        if (dismissed) {
            jdbc.sql("insert into improvement_dismissals (project_id, proposal_id, dismissed_at) values (?, ?, ?)")
                    .params(projectId.toString(), proposalId, Timestamp.from(clock.instant())).update();
        }
    }

    /** Código do frontend da versão e respostas de exemplo da API, para a prévia das telas rodar no navegador. */
    @Transactional(readOnly = true)
    public PreviewBundle previewBundle(UUID ownerId, UUID projectId, Integer version) {
        Snapshot snap = projects.snapshot(ownerId, projectId, version);
        Entry entry = analyzed(snap);
        Context ctx = entry.context();
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 0;
        boolean truncated = false;
        for (Map.Entry<String, String> f : ctx.src().all().entrySet()) {
            String p = f.getKey();
            if (!p.startsWith("frontend/") || p.contains("/node_modules/") || p.contains("/.next/") || p.endsWith("package-lock.json")
                    || !p.matches(".+\\.(tsx|ts|jsx|js|mjs|css|json|html)")) {
                continue;
            }
            if (total + f.getValue().length() > MAX_BUNDLE) {
                truncated = true;
                continue;
            }
            total += f.getValue().length();
            sources.put(p, f.getValue());
        }
        List<PreviewPage> pages = ctx.screens().stream()
                .map(s -> new PreviewPage(s.route(), s.file(), s.title(), s.apiCalls(), s.handlesEmpty(), s.handlesError())).toList();
        String appName = ctx.spec() == null ? "App" : ctx.spec().name();
        return new PreviewBundle(snap.version(), ctx.stack().frontend(), "frontend/", pages, sources, mocks(entry.analysis(), appName), truncated);
    }

    // ---------------- apoio ----------------

    private Snapshot current(UUID ownerId, UUID projectId, ImprovementRequest req) {
        Snapshot snap = projects.snapshot(ownerId, projectId, null);
        if (req == null || req.baseVersion() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe a versão base.");
        }
        if (req.baseVersion() != snap.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "O projeto mudou desde a análise (versão atual: " + snap.version() + "). Recarregue a aba Melhorias.");
        }
        return snap;
    }

    private Proposal proposal(Snapshot snap, String proposalId) {
        return analyzed(snap).analysis().proposals().stream().filter(p -> p.id().equals(proposalId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Esta proposta não vale mais para a versão atual. Recarregue a aba Melhorias."));
    }

    private ChangeSet changeSet(Snapshot snap, String proposalId, ImprovementRequest req, boolean applying) {
        Entry entry = analyzed(snap);
        Proposal p = proposal(snap, proposalId);
        Map<String, Object> params = req.params() == null ? Map.of() : req.params();
        if (!Boolean.TRUE.equals(req.useAi())) {
            return improvements.changes(entry.context(), p, params);
        }
        if (ai == null) {
            throw new ApiException(HttpStatus.CONFLICT, "A IA do NOVA não está configurada neste ambiente.");
        }
        if (applying && req.files() != null && !req.files().isEmpty()) {
            return fromFiles(entry.context(), req.files(), "Alteração revisada na prévia.", true);
        }
        ChangeSet draft = null;
        if (p.automatic()) {
            try {
                draft = improvements.changes(entry.context(), p, params);
            } catch (ApiException e) {
                log.debug("Sem rascunho automático para {}: {}", p.id(), e.getMessage());
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        int size = 0;
        List<String> relevant = new ArrayList<>(p.where());
        if (draft != null) {
            draft.changes().stream().filter(c -> c.action() == ChangeAction.EDIT).map(FileChange::path).forEach(relevant::add);
        }
        for (String path : relevant) {
            String text = entry.context().src().get(path);
            if (text != null && !files.containsKey(path) && size + text.length() <= MAX_AI_CONTEXT) {
                files.put(path, text);
                size += text.length();
            }
        }
        Map<String, String> draftFiles = new LinkedHashMap<>();
        if (draft != null) {
            draft.changes().forEach(c -> draftFiles.put(c.path(), c.after()));
        }
        List<String> tree = entry.context().src().all().keySet().stream().limit(400).toList();
        Optional<ImprovementAi.Result> result;
        try {
            result = ai.apply(new ImprovementAi.Request(p.title(), p.why(), p.howTo(), params, req.note(), tree, files, draftFiles));
        } catch (RuntimeException e) {
            log.warn("IA falhou ao aplicar {}: {}", p.id(), e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "A IA não respondeu agora. Tente de novo ou aplique sem IA.");
        }
        ImprovementAi.Result r = result.filter(x -> x.files() != null && !x.files().isEmpty())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "A IA não conseguiu propor uma alteração para esta melhoria."));
        return fromFiles(entry.context(), r.files().stream().map(f -> new FileIn(f.path(), f.content())).toList(),
                r.summary() == null ? "Alteração proposta pela IA." : r.summary(), true);
    }

    private static ChangeSet fromFiles(Context ctx, List<FileIn> in, String summary, boolean usedAi) {
        if (in.size() > 15) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "A alteração passa de 15 arquivos.");
        }
        List<FileChange> changes = new ArrayList<>();
        for (FileIn f : in) {
            String path = f.path() == null ? "" : f.path().trim().replace('\\', '/');
            String name = Sources.fileName(path);
            if (path.isEmpty() || path.startsWith("/") || path.contains("..") || path.startsWith(".git/") || name.startsWith(".env") && !name.equals(".env.example")
                    || f.content() == null || f.content().length() > MAX_TEXT) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Arquivo não permitido na alteração: " + path);
            }
            String before = ctx.src().get(path);
            changes.add(new FileChange(path, before == null ? ChangeAction.NEW : ChangeAction.EDIT, before, f.content(), null));
        }
        return new ChangeSet(changes, List.of("Revise o código antes de aplicar: ele foi escrito pela IA."), summary, usedAi);
    }

    private Entry analyzed(Snapshot snap) {
        String key = snap.project().getId() + ":" + snap.version();
        synchronized (cache) {
            Entry cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Map<String, String> text = new LinkedHashMap<>();
        snap.files().forEach((path, bytes) -> {
            if (bytes.length <= MAX_TEXT) {
                String s = ProjectService.textOf(bytes);
                if (s != null && s.indexOf('\0') < 0) {
                    text.put(path, s);
                }
            }
        });
        ProjectSpec spec = null;
        try {
            spec = mapper.readValue(snap.project().getSpecJson(), ProjectSpec.class);
        } catch (RuntimeException e) {
            log.debug("Spec ilegível no projeto {}: {}", snap.project().getId(), e.toString());
        }
        Context ctx = analyzer.context(spec, text);
        Entry entry = new Entry(ctx, analyzer.analyze(ctx));
        synchronized (cache) {
            cache.put(key, entry);
        }
        return entry;
    }

    private Set<String> dismissed(UUID projectId) {
        return jdbc.sql("select proposal_id from improvement_dismissals where project_id = ?").param(projectId.toString()).query(String.class).set();
    }

    private List<Applied> applied(UUID projectId) {
        return jdbc.sql("select proposal_id, title, version_number, used_ai, applied_at from improvement_applications where project_id = ? order by applied_at desc, id desc limit 50")
                .param(projectId.toString())
                .query((rs, n) -> new Applied(rs.getString("proposal_id"), rs.getString("title"), rs.getInt("version_number"), rs.getBoolean("used_ai"),
                        rs.getTimestamp("applied_at").toInstant()))
                .list();
    }

    private static List<Mock> mocks(Analysis a, String appName) {
        List<Mock> mocks = new ArrayList<>();
        Map<String, Entity> entities = a.entities().stream().collect(Collectors.toMap(Entity::name, e -> e, (x, y) -> x));
        for (Resource r : a.resources()) {
            Entity e = r.entity() == null ? null : entities.get(r.entity());
            Endpoint list = r.operations().get(Operation.LIST);
            Endpoint read = r.operations().get(Operation.READ);
            if (list != null) {
                List<Map<String, Object>> rows = new ArrayList<>();
                if (e != null) {
                    for (int i = 1; i <= 3; i++) {
                        rows.add(sampleRow(e, i));
                    }
                }
                mocks.add(new Mock("GET", list.path(), rows));
            }
            if (read != null && e != null) {
                mocks.add(new Mock("GET", read.path(), sampleRow(e, 1)));
            }
            for (Endpoint action : r.actions()) {
                if (action.method().equals("GET") && action.path().endsWith("/status") && !action.path().contains("{")) {
                    mocks.add(new Mock("GET", action.path(), Map.of("app", appName, "status", "ok")));
                }
            }
            if (list != null && r.path().endsWith("/status") && e == null) {
                mocks.removeIf(m -> m.path().equals(list.path()));
                mocks.add(new Mock("GET", list.path(), Map.of("app", appName, "status", "ok")));
            }
        }
        return mocks;
    }

    static Map<String, Object> sampleRow(Entity e, int i) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Field f : e.fields()) {
            String n = Names.normalize(f.name());
            Object v = switch (f.kind()) {
                case INTEGER, LONG -> f.id() ? i : i * 3;
                case DECIMAL, DOUBLE -> new BigDecimal(i * 12 + ".90");
                case BOOLEAN -> i % 2 == 1;
                case DATE -> "2026-0" + i + "-1" + i;
                case DATETIME, INSTANT -> "2026-0" + i + "-1" + i + "T10:30:00";
                case UUID -> "00000000-0000-0000-0000-00000000000" + i;
                case ENUM -> f.enumValues() == null || f.enumValues().isEmpty() ? "A" : f.enumValues().get((i - 1) % f.enumValues().size());
                case TEXT, LONG_TEXT -> n.contains("email") ? "pessoa" + i + "@exemplo.com"
                        : n.contains("telefone") || n.contains("phone") ? "(11) 9000" + i + "-000" + i
                        : SpringReader.isStatusName(f.name()) ? "ATIVO" : SpringCode.label(f.name()) + " de exemplo " + i;
                default -> null;
            };
            if (v != null) {
                row.put(f.name(), v);
            }
        }
        row.putIfAbsent("id", i);
        return row;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Usado ao trocar de versão: a análise antiga continua válida, mas não precisa ficar na memória. */
    void evict(UUID projectId) {
        synchronized (cache) {
            cache.keySet().removeIf(k -> k.startsWith(projectId + ":"));
        }
    }

}
