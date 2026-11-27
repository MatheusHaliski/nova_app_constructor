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
    private final com.nova.constructor.project.NetworkService network;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public InsightsService(ProjectService projects, Analyzer analyzer, Improvements improvements, JsonMapper mapper, JdbcClient jdbc, Clock clock,
                           NovaProperties props, com.nova.constructor.project.NetworkService network) {
        this.network = network;
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
        List<ProposalView> views = new ArrayList<>(a.proposals().stream().map(p -> ProposalView.of(p, ai != null, dismissed.contains(p.id()))).toList());
        for (com.nova.constructor.project.NetworkService.Suggestion s : network.open(projectId)) {
            views.add(ProposalView.of(review(s), ai != null, false));
        }
        return new Insights(snap.version(), clock.instant(), a.stack(), a.stages(), a.layers(), a.resources(), a.endpoints(), a.entities(), a.tables(),
                a.screens(), a.dependencies(), views, applied(projectId), ai != null, a.fileCount(), a.testCount(), a.requirements(), a.flows(),
                a.privacy(), a.accessibility(), readiness(views));
    }

    private static final List<String[]> STEPS = List.of(new String[] {"BUSINESS", "Regras do negócio"}, new String[] {"PRIVACY", "Privacidade (LGPD)"},
            new String[] {"SECURITY", "Segurança"}, new String[] {"TESTS", "Testes"}, new String[] {"SCREENS", "Telas"}, new String[] {"CONFIG", "Configuração e deploy"});

    /** Caminho até publicar: cada etapa termina quando não sobra proposta aberta da categoria. */
    static Model.Readiness readiness(List<ProposalView> views) {
        List<Model.ReadinessStep> steps = new ArrayList<>();
        int done = 0;
        for (String[] s : STEPS) {
            List<ProposalView> open = views.stream().filter(v -> v.category().name().equals(s[0]) && "OPEN".equals(v.state())).toList();
            if (open.isEmpty()) {
                done++;
            }
            steps.add(new Model.ReadinessStep(s[0].toLowerCase(java.util.Locale.ROOT), s[1], open.isEmpty(), open.size(),
                    open.isEmpty() ? null : open.getFirst().title()));
        }
        return new Model.Readiness(Math.round(done * 100f / STEPS.size()), steps);
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
        if (proposalId.startsWith(REVIEW)) {
            network.resolve(projectId, Long.parseLong(proposalId.substring(REVIEW.length()), 16), true, version);
        }
        return new ApplyResult(version, set.changes().stream().map(FileChange::path).toList(), set.notes(), set.usedAi());
    }

    @Transactional
    public void dismiss(UUID ownerId, UUID projectId, String proposalId, boolean dismissed) {
        projects.snapshot(ownerId, projectId, null);
        if (proposalId.startsWith(REVIEW) && dismissed) {
            network.resolve(projectId, Long.parseLong(proposalId.substring(REVIEW.length()), 16), false, 0);
            return;
        }
        if (!proposalId.matches("[a-z0-9-]{2,20}-[0-9a-f]{10}")) {
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
        return bundle(entry.context(), entry.analysis(), snap.version());
    }

    private PreviewBundle bundle(Context ctx, Analysis analysis, int version) {
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
        return new PreviewBundle(version, ctx.stack().frontend(), "frontend/", pages, sources, mocks(analysis, appName), truncated);
    }

    public record FileDiff(String path, String change, int added, int removed, String diff) {}

    public record Comparison(int from, int to, List<FileDiff> files, List<String> endpointsAdded, List<String> endpointsRemoved,
                             List<String> entitiesAdded, List<String> entitiesRemoved, List<String> screensAdded, List<String> screensRemoved) {}

    /** Máquina do tempo: o que mudou entre duas versões, arquivo a arquivo e no que a análise enxerga. */
    @Transactional(readOnly = true)
    public Comparison compare(UUID ownerId, UUID projectId, int from, int to) {
        Snapshot a = projects.snapshot(ownerId, projectId, from);
        Snapshot b = projects.snapshot(ownerId, projectId, to);
        Map<String, String> ta = analyzed(a).context().src().all();
        Map<String, String> tb = analyzed(b).context().src().all();
        List<FileDiff> files = new ArrayList<>();
        java.util.Set<String> paths = new java.util.TreeSet<>(a.files().keySet());
        paths.addAll(b.files().keySet());
        for (String p : paths) {
            byte[] x = a.files().get(p);
            byte[] y = b.files().get(p);
            if (x != null && y != null && java.util.Arrays.equals(x, y)) {
                continue;
            }
            String change = x == null ? "ADDED" : y == null ? "REMOVED" : "CHANGED";
            Diff.Result d = Diff.of(ta.get(p), tb.get(p));
            files.add(new FileDiff(p, change, d.added(), d.removed(), d.text()));
        }
        Analysis aa = analyzed(a).analysis();
        Analysis ab = analyzed(b).analysis();
        java.util.function.Function<Analysis, List<String>> eps = x -> x.endpoints().stream().map(e -> e.method() + " " + e.path()).toList();
        java.util.function.Function<Analysis, List<String>> ents = x -> x.entities().stream().map(Entity::name).toList();
        java.util.function.Function<Analysis, List<String>> scr = x -> x.screens().stream().map(Model.Screen::route).toList();
        return new Comparison(a.version(), b.version(), files, minus(eps.apply(ab), eps.apply(aa)), minus(eps.apply(aa), eps.apply(ab)),
                minus(ents.apply(ab), ents.apply(aa)), minus(ents.apply(aa), ents.apply(ab)), minus(scr.apply(ab), scr.apply(aa)), minus(scr.apply(aa), scr.apply(ab)));
    }

    private static List<String> minus(List<String> a, List<String> b) {
        return a.stream().filter(x -> !b.contains(x)).distinct().toList();
    }

    /** Prévia das telas com a proposta aplicada em memória: mostra o resultado antes de gravar. */
    @Transactional(readOnly = true)
    public PreviewBundle proposalBundle(UUID ownerId, UUID projectId, String proposalId, ImprovementRequest req) {
        Snapshot snap = current(ownerId, projectId, req);
        ChangeSet set = changeSet(snap, proposalId, req, true);
        Entry entry = analyzed(snap);
        Map<String, String> files = new LinkedHashMap<>(entry.context().src().all());
        set.changes().forEach(c -> files.put(c.path(), c.after()));
        Context ctx = analyzer.context(entry.context().spec(), files);
        return bundle(ctx, analyzer.analyze(ctx), snap.version() + 1);
    }

    public record Question(String question) {}

    /** Pergunta sobre o projeto, respondida pela IA com base nos arquivos mais ligados às palavras da pergunta. */
    @Transactional(readOnly = true)
    public ImprovementAi.Answer ask(UUID ownerId, UUID projectId, String question) {
        if (ai == null) {
            throw new ApiException(HttpStatus.CONFLICT, "A IA do NOVA não está configurada neste ambiente.");
        }
        if (question == null || question.isBlank() || question.length() > 1000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Escreva a pergunta (até 1000 caracteres).");
        }
        Snapshot snap = projects.snapshot(ownerId, projectId, null);
        Map<String, String> all = analyzed(snap).context().src().all();
        List<String> words = java.util.Arrays.stream(Names.words(question)).map(Names::normalize).filter(w -> w.length() > 3).toList();
        List<Map.Entry<String, String>> ranked = all.entrySet().stream()
                .sorted(java.util.Comparator.comparingInt((Map.Entry<String, String> e) -> -score(e, words))).toList();
        Map<String, String> chosen = new LinkedHashMap<>();
        int size = 0;
        for (Map.Entry<String, String> e : ranked) {
            if (chosen.size() >= 8 || score(e, words) == 0 && !chosen.isEmpty()) {
                break;
            }
            if (size + e.getValue().length() <= MAX_AI_CONTEXT) {
                chosen.put(e.getKey(), e.getValue());
                size += e.getValue().length();
            }
        }
        try {
            return ai.ask(question.trim(), all.keySet().stream().limit(400).toList(), chosen)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "A IA não conseguiu responder esta pergunta."));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("IA falhou ao responder: {}", e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "A IA não respondeu agora. Tente de novo.");
        }
    }

    private static int score(Map.Entry<String, String> e, List<String> words) {
        String hay = Names.normalize(e.getKey()) + " " + e.getValue().toLowerCase(java.util.Locale.ROOT);
        int s = 0;
        for (String w : words) {
            if (Names.normalize(e.getKey()).contains(w)) {
                s += 10;
            }
            s += Math.min(5, countOf(hay, w));
        }
        return s;
    }

    private static int countOf(String hay, String w) {
        int n = 0;
        for (int i = hay.indexOf(w); i >= 0 && n < 50; i = hay.indexOf(w, i + 1)) {
            n++;
        }
        return n;
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

    static final String REVIEW = "review-";

    /** Sugestão de outra pessoa como proposta da aba Melhorias. */
    private static Proposal review(com.nova.constructor.project.NetworkService.Suggestion s) {
        return new Proposal(REVIEW + String.format("%010x", s.id()), "review", Model.Category.REVIEW, Model.Severity.SUGGESTION,
                "REVISÃO · @" + (s.author() == null ? "usuário removido" : s.author()), s.title(), s.body(),
                s.files().stream().map(com.nova.constructor.project.NetworkService.SuggestionFile::path).toList(), List.of(), !s.files().isEmpty(),
                s.files().isEmpty() ? "A sugestão não traz arquivos: siga a descrição de quem sugeriu." : null);
    }

    private com.nova.constructor.project.NetworkService.Suggestion suggestion(UUID projectId, String proposalId) {
        long id = Long.parseLong(proposalId.substring(REVIEW.length()), 16);
        return network.open(projectId).stream().filter(s -> s.id() == id).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Esta sugestão já foi resolvida."));
    }

    private Proposal proposal(Snapshot snap, String proposalId) {
        if (proposalId.startsWith(REVIEW)) {
            return review(suggestion(snap.project().getId(), proposalId));
        }
        return analyzed(snap).analysis().proposals().stream().filter(p -> p.id().equals(proposalId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Esta proposta não vale mais para a versão atual. Recarregue a aba Melhorias."));
    }

    private ChangeSet changeSet(Snapshot snap, String proposalId, ImprovementRequest req, boolean applying) {
        Entry entry = analyzed(snap);
        Proposal p = proposal(snap, proposalId);
        Map<String, Object> params = req.params() == null ? Map.of() : req.params();
        if (proposalId.startsWith(REVIEW) && !Boolean.TRUE.equals(req.useAi())) {
            com.nova.constructor.project.NetworkService.Suggestion s = suggestion(snap.project().getId(), proposalId);
            if (s.files().isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "A sugestão não traz arquivos: siga a descrição de quem sugeriu.");
            }
            ChangeSet set = fromFiles(entry.context(), s.files().stream().map(f -> new FileIn(f.path(), f.content())).toList(),
                    "Sugestão de @" + s.author() + ".", false);
            return new ChangeSet(set.changes(), List.of("Revise: o código veio de outra pessoa da rede."), set.summary(), false);
        }
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
                mocks.add(new Mock("GET", list.path(), rows, list.access()));
            }
            if (read != null && e != null) {
                mocks.add(new Mock("GET", read.path(), sampleRow(e, 1), read.access()));
            }
            for (Endpoint action : r.actions()) {
                if (action.method().equals("GET") && action.path().endsWith("/status") && !action.path().contains("{")) {
                    mocks.add(new Mock("GET", action.path(), Map.of("app", appName, "status", "ok"), action.access()));
                }
            }
            if (list != null && r.path().endsWith("/status") && e == null) {
                mocks.removeIf(m -> m.path().equals(list.path()));
                mocks.add(new Mock("GET", list.path(), Map.of("app", appName, "status", "ok"), list.access()));
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
