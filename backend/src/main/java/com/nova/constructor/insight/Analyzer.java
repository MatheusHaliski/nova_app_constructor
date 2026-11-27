package com.nova.constructor.insight;

import com.nova.constructor.generator.ProjectSpec;
import com.nova.constructor.insight.Model.Analysis;
import com.nova.constructor.insight.Model.Category;
import com.nova.constructor.insight.Model.Dependencies;
import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.Layer;
import com.nova.constructor.insight.Model.LayerItem;
import com.nova.constructor.insight.Model.Operation;
import com.nova.constructor.insight.Model.Proposal;
import com.nova.constructor.insight.Model.Resource;
import com.nova.constructor.insight.Model.Role;
import com.nova.constructor.insight.Model.Screen;
import com.nova.constructor.insight.Model.Severity;
import com.nova.constructor.insight.Model.Stack;
import com.nova.constructor.insight.Model.Stage;
import com.nova.constructor.insight.Model.StageStatus;
import com.nova.constructor.project.SecretScanner;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Monta a análise de uma versão: lê o código de cada stack e calcula camadas, API, dados, telas, saúde e propostas. */
@Component
public class Analyzer {

    private final Improvements improvements;

    public Analyzer(Improvements improvements) {
        this.improvements = improvements;
    }

    /** Contexto da análise, também usado pelas propostas na hora de aplicar. */
    record Context(ProjectSpec spec, Sources src, Facts facts, Stack stack, List<Resource> resources, List<Screen> screens,
                   List<SecretScanner.Finding> secrets) {}

    public Analysis analyze(ProjectSpec spec, Map<String, String> files) {
        return analyze(context(spec, files));
    }

    Analysis analyze(Context ctx) {
        ProjectSpec spec = ctx.spec();
        Map<String, String> files = ctx.src().all();
        Facts facts = ctx.facts();
        Dependencies deps = CommonReader.dependencies(ctx.src(), spec == null ? List.of() : spec.connectorsOrEmpty(), CommonReader.env(ctx.src()));
        List<Proposal> proposals = new ArrayList<>(improvements.detect(ctx, deps));
        List<Model.PrivacyFinding> privacy = Extras.privacy(ctx);
        List<Model.A11yFinding> a11y = Extras.accessibility(ctx);
        proposals.addAll(Extras.privacyProposals(ctx, privacy));
        proposals.addAll(Extras.a11yProposals(a11y));
        return new Analysis(ctx.stack(), layers(facts), facts.endpoints, ctx.resources(), facts.entities, facts.tables, facts.screens, deps,
                stages(ctx, proposals, privacy), proposals, files.size(), facts.testCount, Extras.requirements(ctx), Extras.flows(ctx), privacy, a11y);
    }

    Context context(ProjectSpec spec, Map<String, String> files) {
        Sources src = new Sources(files);
        Facts facts = new Facts();
        String backend = spec == null ? detectBackend(src) : spec.backend().id();
        switch (backend) {
            case "spring-boot" -> SpringReader.read(src, facts);
            case "nestjs" -> NestReader.read(src, facts);
            case "fastapi" -> FastApiReader.read(src, facts);
            default -> { }
        }
        FrontendReader.read(src, facts);
        facts.tables.addAll(CommonReader.tables(src));
        String pom = src.get("backend/pom.xml");
        String gradle = src.get("backend/build.gradle.kts");
        String build = pom != null ? pom : gradle != null ? gradle : "";
        boolean jpa = build.contains("spring-boot-starter-data-jpa");
        boolean flyway = build.contains("flyway-core") || build.contains("spring-boot-starter-flyway");
        Stack stack = new Stack(backend, spec == null ? null : spec.backend().language(), pom != null ? "maven" : gradle != null ? "gradle" : null,
                facts.frontendFramework, spec == null || spec.database() == null ? null : spec.database().id(),
                facts.securityPresent, jpa, flyway);
        List<SecretScanner.Finding> secrets = new ArrayList<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            String name = Sources.fileName(e.getKey());
            if (!name.startsWith(".env") && !e.getKey().contains("/node_modules/")) {
                secrets.addAll(SecretScanner.scan(e.getKey(), e.getValue()));
            }
        }
        return new Context(spec, src, facts, stack, resources(facts), facts.screens, secrets);
    }

    private static String detectBackend(Sources src) {
        if (src.has("backend/pom.xml") || src.has("backend/build.gradle.kts")) {
            return "spring-boot";
        }
        if (src.has("backend/nest-cli.json")) {
            return "nestjs";
        }
        return src.has("backend/pyproject.toml") ? "fastapi" : "desconhecido";
    }

    /** Agrupa os endpoints pelo caminho da coleção e classifica as operações CRUD. */
    static List<Resource> resources(Facts facts) {
        Map<String, List<Endpoint>> byCollection = new LinkedHashMap<>();
        for (Endpoint e : facts.endpoints) {
            byCollection.computeIfAbsent(collectionOf(e.path()), k -> new ArrayList<>()).add(e);
        }
        List<Resource> list = new ArrayList<>();
        for (Map.Entry<String, List<Endpoint>> entry : byCollection.entrySet()) {
            String collection = entry.getKey();
            Map<Operation, Endpoint> ops = new EnumMap<>(Operation.class);
            List<Endpoint> actions = new ArrayList<>();
            for (Endpoint e : entry.getValue()) {
                String rest = e.path().substring(Math.min(collection.length(), e.path().length()));
                boolean item = rest.matches("/\\{[^}/]+\\}");
                Operation op = switch (e.method()) {
                    case "GET" -> rest.isEmpty() ? Operation.LIST : item ? Operation.READ : null;
                    case "POST" -> rest.isEmpty() ? Operation.CREATE : null;
                    case "PUT", "PATCH" -> item ? Operation.UPDATE : null;
                    case "DELETE" -> item ? Operation.DELETE : null;
                    default -> null;
                };
                if (op != null && !ops.containsKey(op)) {
                    ops.put(op, e);
                } else {
                    actions.add(e);
                }
            }
            String name = lastSegment(collection);
            list.add(new Resource(collection, name, entityFor(name, entry.getValue(), facts), ops, actions));
        }
        return list;
    }

    static String collectionOf(String path) {
        int var = path.indexOf("/{");
        return var < 0 ? path : path.substring(0, var);
    }

    static String lastSegment(String path) {
        String[] parts = path.split("/");
        return parts.length == 0 ? path : parts[parts.length - 1];
    }

    private static String entityFor(String name, List<Endpoint> endpoints, Facts facts) {
        String key = Names.key(name);
        for (Entity e : facts.entities) {
            if (Names.key(e.name()).equals(key) || Names.key(e.table()).equals(key)) {
                return e.name();
            }
        }
        for (Endpoint ep : endpoints) {
            for (Entity e : facts.entities) {
                String text = facts.controllers.stream().filter(c -> c.file().equals(ep.file())).findFirst()
                        .map(c -> c.name()).orElse("");
                if (!text.isEmpty() && Names.key(text.replace("Controller", "")).equals(Names.key(e.name()))) {
                    return e.name();
                }
            }
        }
        return null;
    }

    private static final Map<Role, String[]> LABELS = Map.of(
            Role.CONTROLLER, new String[] {"Controllers e rotas", "Recebem as requisições HTTP e chamam os serviços. Não devem guardar regra de negócio."},
            Role.SERVICE, new String[] {"Serviços", "Guardam as regras do negócio: validações, cálculos e transações."},
            Role.REPOSITORY, new String[] {"Repositórios", "Leem e gravam no banco de dados."},
            Role.ENTITY, new String[] {"Entidades", "Representam os dados guardados e as tabelas do banco."},
            Role.CONFIG, new String[] {"Configuração", "Inicialização, segurança, conexões e propriedades por ambiente."},
            Role.TEST, new String[] {"Testes", "Conferem automaticamente que o código continua funcionando."},
            Role.PAGE, new String[] {"Páginas", "Telas do frontend, cada uma com a sua rota."},
            Role.COMPONENT, new String[] {"Componentes", "Peças de interface reaproveitadas pelas páginas."},
            Role.OTHER, new String[] {"Outros", "DTOs, esquemas e código de apoio."});

    private static List<Layer> layers(Facts facts) {
        Map<Role, List<LayerItem>> byRole = new EnumMap<>(Role.class);
        for (Facts.Item item : facts.items) {
            byRole.computeIfAbsent(item.role(), r -> new ArrayList<>()).add(new LayerItem(item.name(), item.path(), item.detail()));
        }
        for (Screen s : facts.screens) {
            byRole.computeIfAbsent(Role.PAGE, r -> new ArrayList<>()).add(new LayerItem(s.route(), s.file(),
                    s.generatedPlaceholder() ? "modelo inicial, ainda sem conteúdo"
                            : s.apiCalls().isEmpty() ? "sem chamadas à API" : "usa " + String.join(", ", s.apiCalls())));
        }
        List<Layer> layers = new ArrayList<>();
        for (Role role : Role.values()) {
            List<LayerItem> items = byRole.get(role);
            if (items != null && !items.isEmpty()) {
                layers.add(new Layer(role, LABELS.get(role)[0], LABELS.get(role)[1], items));
            }
        }
        return layers;
    }

    private static List<Stage> stages(Context ctx, List<Proposal> proposals, List<Model.PrivacyFinding> privacy) {
        List<Stage> stages = new ArrayList<>();
        Sources src = ctx.src();
        List<String> missing = new ArrayList<>();
        if (!src.has("README.md")) {
            missing.add("README.md");
        }
        if (!src.has(".env.example")) {
            missing.add(".env.example");
        }
        String gitignore = src.get(".gitignore");
        if (gitignore == null || !gitignore.contains(".env")) {
            missing.add(".env no .gitignore");
        }
        if (!src.any("backend/(pom\\.xml|build\\.gradle\\.kts|build\\.gradle|package\\.json|pyproject\\.toml)")) {
            missing.add("arquivo de build do backend");
        }
        stages.add(new Stage("structure", "Estrutura", missing.isEmpty() ? StageStatus.PASS : StageStatus.WARN,
                missing.isEmpty() ? ctx.src().all().size() + " arquivos, organização completa" : "Falta: " + String.join(", ", missing), List.of()));
        stages.add(new Stage("build", "Compilação", StageStatus.NOT_RUN,
                "A compilação e a execução dos testes rodam na execução em sandbox, prevista para a Fase 6.", List.of()));
        stages.add(stage("tests", "Testes", Category.TESTS, proposals,
                ctx.facts().testCount + (ctx.facts().testCount == 1 ? " arquivo de teste" : " arquivos de teste")));
        stages.add(stage("security", "Segurança", Category.SECURITY, proposals,
                ctx.stack().auth() ? "Rotas protegidas por token; nenhum segredo nos arquivos" : "Nenhum segredo nos arquivos"));
        stages.add(stage("privacy", "Privacidade (LGPD)", Category.PRIVACY, proposals,
                privacy.isEmpty() ? "Nenhum dado pessoal nas entidades" : privacy.size() + " dados pessoais, nenhum exposto sem login"));
        stages.add(stage("business", "Regras do negócio", Category.BUSINESS, proposals,
                ctx.resources().size() + " recursos na API · " + ctx.facts().entities.size() + " entidades"));
        if (ctx.stack().frontend() == null) {
            stages.add(new Stage("screens", "Telas", StageStatus.NOT_APPLICABLE, "Projeto sem frontend.", List.of()));
        } else {
            stages.add(stage("screens", "Telas", Category.SCREENS, proposals, ctx.screens().size() + " páginas"));
        }
        stages.add(stage("config", "Configuração", Category.CONFIG, proposals, "Variáveis documentadas, CI e contêineres em ordem"));
        return stages;
    }

    private static Stage stage(String id, String label, Category category, List<Proposal> proposals, String okSummary) {
        List<Proposal> mine = proposals.stream().filter(p -> p.category() == category).toList();
        if (mine.isEmpty()) {
            return new Stage(id, label, StageStatus.PASS, okSummary, List.of());
        }
        long gaps = mine.stream().filter(p -> p.severity() == Severity.GAP).count();
        String summary = gaps > 0 ? gaps + (gaps == 1 ? " lacuna" : " lacunas") + (mine.size() > gaps ? " e " + (mine.size() - gaps) + " sugestões" : "")
                : mine.size() + (mine.size() == 1 ? " sugestão" : " sugestões");
        return new Stage(id, label, gaps > 0 ? StageStatus.FAIL : StageStatus.WARN, summary, mine.stream().map(Proposal::id).toList());
    }
}
