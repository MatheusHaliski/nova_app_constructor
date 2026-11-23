package com.nova.constructor.generator;

import com.nova.constructor.catalog.Catalog;
import com.nova.constructor.catalog.CatalogService;
import com.nova.constructor.common.ApiException;
import com.nova.constructor.generator.GenerationPlan.Artifact;
import com.nova.constructor.generator.GenerationPlan.Commands;
import com.nova.constructor.generator.GenerationPlan.Env;
import com.nova.constructor.generator.GenerationPlan.Side;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Transforma a especificação do formulário num plano de geração: valida as escolhas contra o catálogo (existência,
 * disponibilidade e compatibilidade), preenche os padrões e resolve pacotes, plugins, variáveis e comandos.
 */
@Service
public class PlanService {

    private final CatalogService catalogService;

    public PlanService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public GenerationPlan plan(ProjectSpec input) {
        Catalog catalog = catalogService.get();
        List<String> warnings = new ArrayList<>();

        if (catalog.projectTypes().stream().noneMatch(t -> t.id().equals(input.type()))) {
            throw invalid("Tipo de projeto desconhecido: " + input.type());
        }

        // ---- Backend ----
        Catalog.Stack backend = catalog.backend(input.backend().id())
                .orElseThrow(() -> invalid("Backend desconhecido: " + input.backend().id()));
        if (!backend.available()) {
            throw invalid("O gerador de " + backend.name() + " ainda não está disponível.");
        }
        String backendVersion = pick(input.backend().version(), backend.versions(), backend.defaultVersion(), "Versão do " + backend.name());
        String languageId = input.backend().language() == null ? backend.languages().getFirst() : input.backend().language();
        if (!backend.languages().contains(languageId)) {
            throw invalid(backend.name() + " não usa a linguagem " + languageId + ". Opções: " + String.join(", ", backend.languages()) + ".");
        }
        Catalog.Language language = catalog.language(languageId).orElseThrow(() -> invalid("Linguagem desconhecida: " + languageId));
        String languageVersion = pick(input.backend().languageVersion(), language.versions(), language.defaultVersion(), "Versão de " + language.name());
        String buildToolId = input.backend().buildTool() == null ? backend.defaultBuildTool() : input.backend().buildTool();
        Catalog.BuildTool buildTool = backend.buildTool(buildToolId)
                .orElseThrow(() -> invalid(backend.name() + " não oferece a ferramenta de build " + buildToolId + "."));

        // ---- Banco, conectores, IA e recursos ----
        Catalog.Database database = null;
        String databaseVersion = null;
        if (input.database() != null) {
            database = catalog.database(input.database().id()).orElseThrow(() -> invalid("Banco desconhecido: " + input.database().id()));
            if (!database.available()) {
                throw invalid("O banco " + database.name() + " ainda não está disponível.");
            }
            databaseVersion = pick(input.database().version(), database.versions(), database.defaultVersion(), "Versão do " + database.name());
        }
        List<Catalog.Connector> connectors = new ArrayList<>();
        for (String id : new LinkedHashSet<>(input.connectorsOrEmpty())) {
            connectors.add(catalog.connector(id).orElseThrow(() -> invalid("Conector desconhecido: " + id)));
        }
        Catalog.AiProvider ai = null;
        String aiModel = null;
        if (input.ai() != null) {
            ai = catalog.aiProvider(input.ai().provider()).orElseThrow(() -> invalid("Provedor de IA desconhecido: " + input.ai().provider()));
            if (!ai.available()) {
                throw invalid("A integração com " + ai.name() + " ainda não está disponível.");
            }
            aiModel = pick(input.ai().model(), ai.models(), ai.defaultModel(), "Modelo de " + ai.name());
        }
        List<String> features = input.features() == null
                ? catalog.features().stream().filter(Catalog.Feature::enabledByDefault).map(Catalog.Feature::id).toList()
                : List.copyOf(new LinkedHashSet<>(input.features()));
        for (String f : features) {
            if (catalog.features().stream().noneMatch(x -> x.id().equals(f))) {
                throw invalid("Recurso desconhecido: " + f);
            }
        }

        // ---- Pacotes do backend ----
        boolean hasDatabase = database != null;
        List<String> chosenBackend = resolvePackageIds(backend, input.backend().packages(), features, hasDatabase, buildToolId, warnings);
        Map<String, Artifact> deps = new LinkedHashMap<>();
        Map<String, Artifact> plugins = new LinkedHashMap<>();
        collect(backend, chosenBackend, features, deps, plugins);
        if (hasDatabase) {
            addExternal(deps, database.packages().get(backend.id()), "Banco: " + database.name());
        } else if (features.contains("auth")) {
            warnings.add("Autenticação sem banco: os usuários não terão onde ser guardados.");
        }
        for (Catalog.Connector c : connectors) {
            addExternal(deps, c.packages().get(backend.id()), "Conector: " + c.name());
        }
        if (ai != null) {
            addExternal(deps, ai.packages().get(backend.id()), "IA externa: " + ai.name());
        }

        // ---- Frontend ----
        Side frontendSide = null;
        List<String> chosenFrontend = null;
        String frontendVersion = null;
        if (input.frontend() != null) {
            Catalog.Stack frontend = catalog.frontend(input.frontend().id())
                    .orElseThrow(() -> invalid("Frontend desconhecido: " + input.frontend().id()));
            if (!frontend.available()) {
                throw invalid("O gerador de " + frontend.name() + " ainda não está disponível.");
            }
            frontendVersion = pick(input.frontend().version(), frontend.versions(), frontend.defaultVersion(), "Versão do " + frontend.name());
            chosenFrontend = resolvePackageIds(frontend, input.frontend().packages(), features, false, frontend.defaultBuildTool(), warnings);
            Map<String, Artifact> fDeps = new LinkedHashMap<>();
            Map<String, Artifact> fPlugins = new LinkedHashMap<>();
            collect(frontend, chosenFrontend, features, fDeps, fPlugins);
            Catalog.BuildTool fTool = frontend.buildTool(frontend.defaultBuildTool()).orElseThrow();
            frontendSide = new Side(frontend.id(), frontend.name(), frontendVersion, "typescript", "5.9",
                    List.copyOf(fDeps.values()), List.copyOf(fPlugins.values()),
                    new Commands(fTool.name(), fTool.build(), fTool.run(), fTool.test()));
        } else if (!input.screensOrEmpty().isEmpty()) {
            warnings.add("Telas informadas sem frontend: elas não serão geradas.");
        }

        // ---- Variáveis de ambiente ----
        List<Env> env = new ArrayList<>();
        if (hasDatabase) {
            env.add(new Env("DATABASE_URL", databaseUrl(database, backend.id()), false, "Banco: " + database.name()));
            env.add(new Env("DATABASE_USER", "app", false, "Banco: " + database.name()));
            env.add(new Env("DATABASE_PASSWORD", "", true, "Banco: " + database.name()));
        }
        if (features.contains("auth")) {
            env.add(new Env("JWT_SECRET", "", true, "Recurso: autenticação"));
            env.add(new Env("APP_ADMIN_USER", "admin", false, "Recurso: autenticação"));
            env.add(new Env("APP_ADMIN_PASSWORD", "", true, "Recurso: autenticação"));
        }
        for (Catalog.Connector c : connectors) {
            for (Catalog.EnvVar v : c.env()) {
                env.add(new Env(v.name(), v.example(), v.secret(), "Conector: " + c.name()));
            }
        }
        if (ai != null) {
            for (Catalog.EnvVar v : ai.env()) {
                env.add(new Env(v.name(), v.example(), v.secret(), "IA externa: " + ai.name()));
            }
        }
        if (frontendSide != null) {
            env.add(new Env("API_URL", "http://localhost:8080", false, "Frontend"));
        }

        String slug = slug(input.name());
        ProjectSpec normalized = new ProjectSpec(input.name().trim(), blankToNull(input.description()), blankToNull(input.objective()),
                input.type(), input.mode() == null ? "ASSISTED" : input.mode(),
                new ProjectSpec.Backend(backend.id(), backendVersion, languageId, languageVersion, buildToolId, chosenBackend),
                frontendSide == null ? null : new ProjectSpec.Frontend(frontendSide.id(), frontendVersion, chosenFrontend),
                hasDatabase ? new ProjectSpec.Database(database.id(), databaseVersion) : null,
                connectors.stream().map(Catalog.Connector::id).toList(),
                ai == null ? null : new ProjectSpec.Ai(ai.id(), aiModel),
                features,
                frontendSide == null ? List.of() : cleanScreens(input.screensOrEmpty()));

        Side backendSide = new Side(backend.id(), backend.name(), backendVersion, language.name(), languageVersion,
                List.copyOf(deps.values()), List.copyOf(plugins.values()),
                new Commands(buildTool.name(), buildTool.build(), buildTool.run(), buildTool.test()));
        return new GenerationPlan(normalized, slug, backendSide, frontendSide, env, List.of(), warnings);
    }

    /** Pacotes efetivos: obrigatórios + (escolhidos ou padrões) + os exigidos pelos recursos marcados. */
    private List<String> resolvePackageIds(Catalog.Stack stack, List<String> requested, List<String> features,
                                           boolean hasDatabase, String buildTool, List<String> warnings) {
        Set<String> ids = new LinkedHashSet<>();
        if (requested != null) {
            for (String id : requested) {
                if (stack.packages().stream().noneMatch(p -> p.id().equals(id))) {
                    throw invalid("Pacote desconhecido em " + stack.name() + ": " + id);
                }
            }
        }
        for (Catalog.Pkg p : stack.packages()) {
            boolean wanted = p.required()
                    || (p.feature() != null && features.contains(p.feature()))
                    || (requested == null ? p.selectedByDefault() : requested.contains(p.id()));
            if (!wanted) {
                continue;
            }
            if ("database".equals(p.needs()) && !hasDatabase) {
                if (requested != null && requested.contains(p.id())) {
                    warnings.add(p.name() + " foi ignorado porque nenhum banco foi escolhido.");
                }
                continue;
            }
            if (p.buildTools() != null && !p.buildTools().isEmpty() && !p.buildTools().contains(buildTool)) {
                warnings.add(p.name() + " não está disponível para " + buildTool + " e foi ignorado.");
                continue;
            }
            ids.add(p.id());
        }
        return List.copyOf(ids);
    }

    private void collect(Catalog.Stack stack, List<String> ids, List<String> features, Map<String, Artifact> deps, Map<String, Artifact> plugins) {
        for (Catalog.Pkg p : stack.packages()) {
            if (!ids.contains(p.id())) {
                continue;
            }
            String reason = p.required() ? "Obrigatório"
                    : p.feature() != null && features.contains(p.feature()) ? "Recurso: " + p.feature() : "Escolhido";
            boolean plugin = "plugin".equals(p.kind());
            for (String coordinate : p.artifact().split(",")) {
                String scope = p.scope() != null ? p.scope() : plugin ? "dev" : "compile";
                (plugin ? plugins : deps).putIfAbsent(coordinate.trim(), new Artifact(coordinate.trim(), scope, p.kind(), reason));
            }
        }
    }

    /** Pacotes vindos de banco, conector ou IA: {@code coordenada} ou {@code coordenada|escopo}. */
    private void addExternal(Map<String, Artifact> deps, List<String> coordinates, String reason) {
        if (coordinates == null) {
            return;
        }
        for (String raw : coordinates) {
            String[] parts = raw.split("\\|");
            deps.putIfAbsent(parts[0], new Artifact(parts[0], parts.length > 1 ? parts[1] : "compile", "dependency", reason));
        }
    }

    private static String databaseUrl(Catalog.Database db, String backendId) {
        boolean mysql = "mysql".equals(db.id());
        return switch (backendId) {
            case "spring-boot" -> mysql ? "jdbc:mysql://localhost:3306/app" : "jdbc:postgresql://localhost:5432/app";
            case "fastapi" -> mysql ? "mysql+pymysql://localhost:3306/app" : "postgresql+psycopg://localhost:5432/app";
            default -> mysql ? "mysql://localhost:3306/app" : "postgres://localhost:5432/app";
        };
    }

    private static String pick(String requested, List<String> options, String fallback, String label) {
        if (requested == null || requested.isBlank()) {
            return fallback;
        }
        if (!options.contains(requested)) {
            throw invalid(label + " inválida: " + requested + ". Opções: " + String.join(", ", options) + ".");
        }
        return requested;
    }

    private static List<String> cleanScreens(List<String> screens) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : screens) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return List.copyOf(out);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Identificador seguro para pastas, pacotes e nomes de artefato. */
    public static String slug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) {
            slug = "app" + (slug.isEmpty() ? "" : "-" + slug);
        }
        return slug.length() > 40 ? slug.substring(0, 40).replaceAll("-+$", "") : slug;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
