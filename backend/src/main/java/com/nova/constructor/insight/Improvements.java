package com.nova.constructor.insight;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.generator.GeneratorService;
import com.nova.constructor.generator.PlanService;
import com.nova.constructor.generator.ProjectSpec;
import com.nova.constructor.insight.Analyzer.Context;
import com.nova.constructor.insight.Model.Category;
import com.nova.constructor.insight.Model.ChangeAction;
import com.nova.constructor.insight.Model.ChangeSet;
import com.nova.constructor.insight.Model.Dependencies;
import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.EnvVar;
import com.nova.constructor.insight.Model.Field;
import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Model.FileChange;
import com.nova.constructor.insight.Model.Operation;
import com.nova.constructor.insight.Model.Option;
import com.nova.constructor.insight.Model.Param;
import com.nova.constructor.insight.Model.Proposal;
import com.nova.constructor.insight.Model.Resource;
import com.nova.constructor.insight.Model.Screen;
import com.nova.constructor.insight.Model.Severity;
import com.nova.constructor.project.SecretScanner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Propostas de melhoria: o que a análise encontrou e como corrigir. Cada tipo sabe detectar a lacuna, oferecer opções
 * editáveis e escrever a correção. Sem aplicação automática, a proposta traz o passo a passo e pode ser aplicada pela IA.
 */
@Component
public class Improvements {

    static final String RESOURCE = "resource";
    static final String CRUD = "crud";
    static final String STATUS = "status";
    static final String TESTS = "tests";
    static final String ENV = "env";
    static final String CI = "ci";
    static final String DOCKER = "docker";
    static final String SECRET = "secret";
    static final String PAGE = "page";
    static final String STATES = "screen-states";
    static final String AUTH = "auth";

    private static final Set<String> STOP_WORDS = Set.of("meu", "meus", "minha", "minhas", "my", "lista", "listagem", "de", "da", "do", "das",
            "dos", "e", "gerenciar", "gestao", "tela", "pagina", "todos", "todas", "ver", "the", "of", "detalhe", "detalhes", "novo", "nova", "criar",
            "editar", "adicionar", "cadastrar", "registrar", "detail", "new", "edit");
    private static final Set<String> NON_DATA = Set.of("home", "inicio", "login", "entrar", "cadastro", "cadastrar", "registro", "signup",
            "perfil", "profile", "conta", "configuracao", "configuraco", "setting", "sobre", "about", "contato", "contact", "ajuda", "help",
            "dashboard", "painel", "admin", "checkout", "carrinho", "cart", "busca", "search", "pesquisa", "landing", "faq", "termo",
            "privacidade", "politica", "sair", "logout", "erro", "error", "notificaco", "notificacao", "mapa", "relatorio");
    private static final Pattern STATUS_VALUE = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");
    private static final Pattern FIELD_NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,39}");
    private static final Pattern API_PATH = Pattern.compile("/[a-z0-9][a-z0-9/_-]{0,99}");
    private static final List<String> FIELD_TYPES = List.of("TEXT", "LONG_TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME", "STATUS");

    private final GeneratorService generator;

    public Improvements(GeneratorService generator) {
        this.generator = generator;
    }

    // =====================================================================================================
    // Detecção
    // =====================================================================================================

    List<Proposal> detect(Context ctx, Dependencies deps) {
        List<Proposal> list = new ArrayList<>();
        boolean spring = "spring-boot".equals(ctx.stack().backend()) && ctx.facts().basePackage != null;
        resources(ctx, spring, list);
        crudGaps(ctx, spring, list);
        statusGaps(ctx, spring, list);
        pages(ctx, list);
        tests(ctx, spring, list);
        env(deps, list);
        secrets(ctx, list);
        ciAndDocker(ctx, list);
        screenStates(ctx, list);
        auth(ctx, list);
        return list;
    }

    private record Candidate(String screen, String route, String file, String entity, String path, String key) {}

    /** Telas de dados (pelo nome) que nenhum recurso da API atende. */
    private static List<Candidate> candidates(Context ctx) {
        Map<String, Candidate> found = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(ctx.spec() == null ? List.of() : ctx.spec().screensOrEmpty());
        for (Screen s : ctx.screens()) {
            if (!s.route().equals("/") && !s.route().contains("[")) {
                names.add(s.route().substring(s.route().lastIndexOf('/') + 1));
            }
        }
        Set<String> known = new LinkedHashSet<>();
        ctx.resources().forEach(r -> known.add(Names.key(r.name())));
        ctx.facts().entities.forEach(e -> known.add(Names.key(e.name())));
        Set<String> linked = new LinkedHashSet<>();
        if (ctx.spec() != null) {
            ctx.spec().screenSpecsOrEmpty().stream().filter(s -> s.entity() != null).forEach(s -> {
                linked.add(s.name());
                linked.add(PlanService.slug(s.name()));
            });
        }
        for (String name : names) {
            if (linked.contains(name)) {
                continue;
            }
            List<String> words = new ArrayList<>();
            for (String w : Names.words(name)) {
                String n = Names.normalize(w);
                if (!n.isEmpty() && !STOP_WORDS.contains(n)) {
                    words.add(n);
                }
            }
            if (words.isEmpty()) {
                continue;
            }
            String key = Names.key(String.join("", words));
            if (NON_DATA.contains(key) || NON_DATA.contains(Names.singular(words.getFirst())) || known.contains(key) || found.containsKey(key)) {
                continue;
            }
            String entity = Names.pascal(String.join(" ", words.stream().map(Names::singular).toList()));
            String route = "/" + PlanService.slug(name);
            Screen screen = ctx.screens().stream().filter(s -> s.route().equals(route)).findFirst().orElse(null);
            found.put(key, new Candidate(name, route, screen == null ? null : screen.file(), entity, "/api/" + String.join("-", words), key));
        }
        return new ArrayList<>(found.values());
    }

    private void resources(Context ctx, boolean spring, List<Proposal> list) {
        boolean security = ctx.stack().auth();
        for (Candidate c : candidates(ctx)) {
            List<Param> params = new ArrayList<>();
            params.add(new Param("entity", "Nome da entidade", "TEXT", c.entity(), null, "Em PascalCase, no singular (ex.: Produto)."));
            params.add(new Param("path", "Caminho da API", "TEXT", c.path(), null, "Rota da coleção; o item fica em /{id}."));
            params.add(new Param("fields", "Campos", "FIELDS", defaultFields(c.key()), typeOptions(),
                    "Tipos: texto, texto longo, inteiro, decimal, sim/não, data, data e hora ou status (lista de valores)."));
            params.add(new Param("operations", "Operações", "MULTI", List.of("LIST", "READ", "CREATE", "UPDATE", "DELETE"), operationOptions(), null));
            if (security) {
                params.add(new Param("readAccess", "Quem pode ler", "SELECT", "PUBLIC", accessOptions(), "Listar e ver um item."));
                params.add(new Param("writeAccess", "Quem pode alterar", "SELECT", "AUTHENTICATED", accessOptions(), "Criar, editar e excluir."));
            }
            params.add(new Param("tests", "Gerar testes da API", "BOOLEAN", true, null, null));
            if ("nextjs".equals(ctx.stack().frontend())) {
                params.add(new Param("page", "Ligar a tela " + c.route() + " à API", "BOOLEAN", true, null,
                        "Troca a página de modelo por uma lista com estados de vazio e de erro."));
                params.add(new Param("route", "Rota da tela", "TEXT", c.route(), null, null));
            }
            list.add(new Proposal(id(RESOURCE, c.key()), RESOURCE, Category.BUSINESS, Severity.GAP, "API · " + c.screen().toUpperCase(Locale.ROOT),
                    "Criar os dados e a API de " + c.screen(),
                    "A tela \"" + c.screen() + "\" mostra dados, mas o backend não tem entidade nem rota para eles. Sem isso a tela fica vazia "
                            + "e nada é guardado.",
                    c.file() == null ? List.of() : List.of(c.file()), params, spring,
                    spring ? null : "Crie a entidade, o acesso ao banco e as rotas de listar, ver, criar, editar e excluir em " + c.path() + "."));
        }
    }

    private void crudGaps(Context ctx, boolean spring, List<Proposal> list) {
        for (Entity e : ctx.facts().entities) {
            Resource r = ctx.resources().stream().filter(x -> e.name().equals(x.entity())).findFirst().orElse(null);
            Set<Operation> missing = EnumSet.allOf(Operation.class);
            if (r != null) {
                missing.removeAll(r.operations().keySet());
            }
            boolean auto = spring && e.repository() != null;
            if (!e.writable()) {
                missing.removeAll(List.of(Operation.CREATE, Operation.UPDATE));
            }
            if (missing.isEmpty()) {
                continue;
            }
            String path = r != null ? r.path() : "/api/" + Names.plural(Names.kebab(e.name()));
            List<Param> params = new ArrayList<>();
            params.add(new Param("operations", "Operações que faltam", "MULTI", missing.stream().map(Enum::name).toList(),
                    operationOptions().stream().filter(o -> missing.contains(Operation.valueOf(o.value()))).toList(), null));
            params.add(new Param("path", "Caminho da API", "TEXT", path, null, null));
            if (ctx.stack().auth()) {
                params.add(new Param("readAccess", "Quem pode ler", "SELECT", "AUTHENTICATED", accessOptions(), null));
                params.add(new Param("writeAccess", "Quem pode alterar", "SELECT", "AUTHENTICATED", accessOptions(), null));
            }
            params.add(new Param("tests", "Gerar testes", "BOOLEAN", true, null, null));
            String names = SpringCode.describe(missing);
            List<String> where = new ArrayList<>(List.of(e.file()));
            if (r != null) {
                r.operations().values().stream().map(Endpoint::file).distinct().forEach(where::add);
            }
            list.add(new Proposal(id(CRUD, e.name()), CRUD, Category.BUSINESS, Severity.GAP, "API · " + e.name().toUpperCase(Locale.ROOT),
                    r == null ? "Criar a API de " + e.name() : "Completar a API de " + e.name(),
                    r == null ? "A entidade " + e.name() + " existe, mas nenhuma rota da API dá acesso a ela."
                            : "A API de " + e.name() + " não permite " + names + ". Sem isso, corrigir um dado exige mexer direto no banco.",
                    where, params, auto,
                    auto ? null : "Adicione rotas para " + names + " em " + path + "."
                            + (e.repository() == null && "spring-boot".equals(e.stack()) ? " Antes, crie um repositório para " + e.name() + "." : "")));
        }
    }

    private void statusGaps(Context ctx, boolean spring, List<Proposal> list) {
        for (Entity e : ctx.facts().entities) {
            for (Field f : e.fields()) {
                if (!SpringReader.isStatusName(f.name()) || !(f.kind() == FieldKind.ENUM || f.kind() == FieldKind.TEXT)) {
                    continue;
                }
                Resource r = ctx.resources().stream().filter(x -> e.name().equals(x.entity())).findFirst().orElse(null);
                boolean endpoint = ctx.facts().endpoints.stream().anyMatch(ep -> !ep.method().equals("GET")
                        && Names.normalize(ep.path()).contains(Names.normalize(f.name()))
                        && (r == null || ep.path().startsWith(r.path())));
                Set<String> writers = ctx.facts().statusWriters.getOrDefault(e.name(), Set.of());
                if (endpoint || !writers.isEmpty()) {
                    continue;
                }
                String collection = r != null ? r.path() : "/api/" + Names.plural(Names.kebab(e.name()));
                List<String> values = f.enumValues() != null && !f.enumValues().isEmpty() ? f.enumValues() : defaultStatuses(Names.key(e.name()));
                List<String> allowed = values.size() > 1 ? values.subList(1, values.size()) : values;
                List<Param> params = new ArrayList<>();
                params.add(new Param("field", "Campo", "HIDDEN", f.name(), null, null));
                params.add(new Param("method", "Método HTTP", "SELECT", "PATCH",
                        List.of(new Option("PATCH", "PATCH"), new Option("PUT", "PUT"), new Option("POST", "POST")), null));
                params.add(new Param("path", "Caminho", "TEXT", collection + "/{id}/" + Names.kebab(f.name()), null, "Precisa ter {id}."));
                params.add(f.kind() == FieldKind.ENUM
                        ? new Param("values", "Valores que podem ser definidos", "MULTI", allowed, values.stream().map(v -> new Option(v, v)).toList(),
                                "O valor inicial (" + values.getFirst() + ") fica de fora: é definido na criação.")
                        : new Param("values", "Valores que podem ser definidos", "LIST", allowed, null, "Em maiúsculas, um por item."));
                if (ctx.stack().auth()) {
                    params.add(new Param("access", "Quem pode alterar", "SELECT", "AUTHENTICATED", accessOptions(), null));
                }
                params.add(new Param("tests", "Gerar testes do novo fluxo", "BOOLEAN", true, null, null));
                boolean auto = spring && "spring-boot".equals(e.stack()) && e.repository() != null && f.writable();
                list.add(new Proposal(id(STATUS, e.name() + "." + f.name()), STATUS, Category.BUSINESS, Severity.GAP,
                        "API · " + e.name().toUpperCase(Locale.ROOT), "Permitir alterar " + f.name() + " de " + e.name(),
                        e.name() + "." + f.name() + " só recebe o valor inicial: nenhuma rota, serviço ou tela muda esse valor depois. "
                                + "Sem isso não há como acompanhar o andamento (por exemplo, marcar como pago, enviado ou cancelado).",
                        List.of(e.file()), params, auto, auto ? null : "Crie uma rota que receba o novo valor, confira se ele é permitido e salve."));
            }
        }
    }

    private void pages(Context ctx, List<Proposal> list) {
        if (!"nextjs".equals(ctx.stack().frontend())) {
            return;
        }
        for (Screen s : ctx.screens()) {
            if (!s.generatedPlaceholder()) {
                continue;
            }
            String key = Names.key(s.route().substring(s.route().lastIndexOf('/') + 1));
            Resource r = ctx.resources().stream().filter(x -> x.operations().containsKey(Operation.LIST)
                    && (Names.key(x.name()).equals(key) || x.entity() != null && Names.key(x.entity()).equals(key))).findFirst().orElse(null);
            if (r == null || r.entity() == null) {
                continue;
            }
            Entity e = ctx.facts().entities.stream().filter(x -> x.name().equals(r.entity())).findFirst().orElse(null);
            if (e == null) {
                continue;
            }
            List<Field> simple = simpleFields(e);
            list.add(new Proposal(id(PAGE, s.route()), PAGE, Category.SCREENS, Severity.SUGGESTION, "TELAS · " + s.route().toUpperCase(Locale.ROOT),
                    "Mostrar os dados de " + e.name() + " na tela " + s.route(),
                    "A tela ainda é o modelo inicial, embora a API já tenha " + r.path() + ".", List.of(s.file()),
                    List.of(new Param("title", "Título da tela", "TEXT", s.title() == null ? SpringCode.label(e.name()) : s.title(), null, null),
                            new Param("columns", "Colunas", "MULTI", simple.stream().limit(6).map(Field::name).toList(),
                                    simple.stream().map(f -> new Option(f.name(), SpringCode.label(f.name()))).toList(), null)),
                    true, null));
        }
    }

    private void tests(Context ctx, boolean spring, List<Proposal> list) {
        if (!spring) {
            return;
        }
        for (Facts.Controller c : ctx.facts().controllers) {
            if (c.testableGets().isEmpty() || c.constructor() == null || ctx.facts().tested(c.name())
                    || c.constructor().stream().anyMatch(p -> p.type().contains("<") || Character.isLowerCase(p.type().charAt(0)) || p.type().equals("String"))) {
                continue;
            }
            List<String> paths = c.testableGets().stream().map(Endpoint::path).toList();
            list.add(new Proposal(id(TESTS, c.file()), TESTS, Category.TESTS, Severity.SUGGESTION, "TESTES · " + c.name().toUpperCase(Locale.ROOT),
                    "Testar " + c.name(), c.name() + " não tem nenhum teste. Uma mudança que quebre " + String.join(", ", paths)
                            + " só seria percebida em produção.", List.of(c.file()),
                    List.of(new Param("paths", "Rotas testadas", "MULTI", paths, paths.stream().map(p -> new Option(p, "GET " + p)).toList(), null)),
                    true, null));
        }
    }

    private void env(Dependencies deps, List<Proposal> list) {
        List<EnvVar> missing = deps.env().stream().filter(v -> !v.documented() && !v.usedIn().isEmpty()).toList();
        if (missing.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EnvVar v : missing) {
            rows.add(new LinkedHashMap<>(Map.of("name", v.name(), "value", v.secret() ? "" : "", "include", true)));
        }
        list.add(new Proposal(id(ENV, String.join(",", missing.stream().map(EnvVar::name).toList())), ENV, Category.CONFIG, Severity.GAP,
                "CONFIGURAÇÃO · VARIÁVEIS", "Documentar " + missing.size() + (missing.size() == 1 ? " variável" : " variáveis") + " no .env.example",
                "O código lê " + String.join(", ", missing.stream().map(EnvVar::name).toList())
                        + ", mas o .env.example não as lista. Quem baixar o projeto não vai saber que precisa preencher.",
                missing.stream().flatMap(v -> v.usedIn().stream()).distinct().limit(6).toList(),
                List.of(new Param("variables", "Variáveis", "ENV", rows, null, "Segredos ficam sem valor de exemplo.")), true, null));
    }

    private void secrets(Context ctx, List<Proposal> list) {
        Map<String, List<SecretScanner.Finding>> byFile = new LinkedHashMap<>();
        for (SecretScanner.Finding f : ctx.secrets()) {
            byFile.computeIfAbsent(f.path(), k -> new ArrayList<>()).add(f);
        }
        for (Map.Entry<String, List<SecretScanner.Finding>> e : byFile.entrySet()) {
            String path = e.getKey();
            boolean config = path.matches(".+\\.(ya?ml|properties)");
            List<Param> params = new ArrayList<>();
            if (config) {
                String text = ctx.src().get(path);
                String[] lines = text.split("\n", -1);
                for (SecretScanner.Finding f : e.getValue()) {
                    params.add(new Param("var_" + f.line(), "Variável para a linha " + f.line(), "TEXT", varName(lines, f.line() - 1, path), null, null));
                }
            }
            list.add(new Proposal(id(SECRET, path), SECRET, Category.SECURITY, Severity.GAP, "SEGURANÇA · " + Sources.fileName(path).toUpperCase(Locale.ROOT),
                    "Tirar credenciais de " + Sources.fileName(path),
                    "Há " + e.getValue().size() + (e.getValue().size() == 1 ? " possível credencial" : " possíveis credenciais") + " escritas no arquivo (linhas "
                            + String.join(", ", e.getValue().stream().map(f -> String.valueOf(f.line())).toList())
                            + "). Quem tiver acesso ao código passa a ter acesso ao serviço. Depois de corrigir, troque a chave se ela já foi usada.",
                    List.of(path), params, config,
                    config ? null : "Leia o valor de uma variável de ambiente e documente o nome no .env.example. Troque a chave se ela já foi usada."));
        }
    }

    private void ciAndDocker(Context ctx, List<Proposal> list) {
        if (ctx.spec() == null) {
            return;
        }
        if (!ctx.src().any("\\.github/workflows/.+\\.ya?ml")) {
            list.add(new Proposal(id(CI, "ci"), CI, Category.CONFIG, Severity.SUGGESTION, "CONFIGURAÇÃO · CI", "Rodar build e testes a cada envio (CI)",
                    "O projeto não tem integração contínua: nada confere o build e os testes quando alguém envia uma alteração.",
                    List.of(), List.of(), true, null));
        }
        if (!ctx.src().has("backend/Dockerfile")) {
            list.add(new Proposal(id(DOCKER, "docker"), DOCKER, Category.CONFIG, Severity.SUGGESTION, "CONFIGURAÇÃO · CONTÊINERES",
                    "Empacotar em contêineres (Docker)", "Não há Dockerfile nem docker-compose: rodar o app com banco e conectores exige instalar tudo à mão.",
                    List.of(), List.of(), true, null));
        }
    }

    private void screenStates(Context ctx, List<Proposal> list) {
        for (Screen s : ctx.screens()) {
            if (s.apiCalls().isEmpty() || s.generatedPlaceholder() || s.handlesEmpty() && s.handlesError()) {
                continue;
            }
            String missing = !s.handlesEmpty() && !s.handlesError() ? "lista vazia nem falha da API" : !s.handlesEmpty() ? "lista vazia" : "falha da API";
            list.add(new Proposal(id(STATES, s.file()), STATES, Category.SCREENS, Severity.SUGGESTION, "TELAS · " + s.route().toUpperCase(Locale.ROOT),
                    "Tratar " + missing + " em " + s.route(),
                    "A tela usa " + String.join(", ", s.apiCalls()) + " mas não mostra nada quando há " + missing + ". A pessoa fica sem saber se carregou.",
                    List.of(s.file()), List.of(), false, "Mostre uma mensagem quando não houver dados e outra, com opção de tentar de novo, quando a API falhar."));
        }
    }

    private void auth(Context ctx, List<Proposal> list) {
        boolean writes = ctx.facts().endpoints.stream().anyMatch(e -> !e.method().equals("GET"));
        if (writes && !ctx.stack().auth()) {
            list.add(new Proposal(id(AUTH, "auth"), AUTH, Category.SECURITY, Severity.SUGGESTION, "SEGURANÇA · ACESSO",
                    "Proteger as rotas que alteram dados", "Qualquer pessoa que souber o endereço da API pode criar, editar ou excluir dados.",
                    ctx.facts().endpoints.stream().filter(e -> !e.method().equals("GET")).map(Endpoint::file).distinct().limit(6).toList(), List.of(), false,
                    "Ative a autenticação por token e exija login nas rotas de escrita. Num projeto novo, marque \"Autenticação\" no formulário."));
        }
    }

    // =====================================================================================================
    // Aplicação
    // =====================================================================================================

    /** Calcula as alterações de uma proposta automática com as opções que o usuário escolheu. */
    ChangeSet changes(Context ctx, Proposal p, Map<String, Object> raw) {
        if (!p.automatic()) {
            throw new ApiException(HttpStatus.CONFLICT, "O NOVA ainda não aplica esta melhoria sozinho. Siga o passo a passo ou use a IA.");
        }
        Opts o = new Opts(p, raw);
        Out out = new Out(ctx.src());
        switch (p.kind()) {
            case RESOURCE -> applyResource(ctx, p, o, out);
            case CRUD -> applyCrud(ctx, p, o, out);
            case STATUS -> applyStatus(ctx, p, o, out);
            case PAGE -> applyPage(ctx, p, o, out);
            case TESTS -> applyTests(ctx, p, o, out);
            case ENV -> applyEnv(ctx, o, out);
            case SECRET -> applySecret(ctx, p, o, out);
            case CI -> applyRegenerated(ctx, "ci", List.of(".github/workflows/ci.yml"), out);
            case DOCKER -> applyRegenerated(ctx, "docker", List.of("backend/Dockerfile", "frontend/Dockerfile", "docker-compose.yml"), out);
            default -> throw new ApiException(HttpStatus.CONFLICT, "Tipo de melhoria desconhecido: " + p.kind());
        }
        if (out.changes.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Com essas opções não há nada para mudar.");
        }
        return new ChangeSet(out.changes, out.notes, out.changes.size() + (out.changes.size() == 1 ? " arquivo" : " arquivos")
                + " (" + out.changes.stream().filter(c -> c.action() == ChangeAction.NEW).count() + " novos)", false);
    }

    /** Arquivos alterados por uma melhoria, na ordem em que aparecem para o usuário. */
    private static final class Out {
        final Sources src;
        final List<FileChange> changes = new ArrayList<>();
        final List<String> notes = new ArrayList<>();

        Out(Sources src) {
            this.src = src;
        }

        String current(String path) {
            return changes.stream().filter(c -> c.path().equals(path)).map(FileChange::after).findFirst().orElse(src.get(path));
        }

        void put(String path, String content, String why) {
            changes.removeIf(c -> c.path().equals(path));
            String before = src.get(path);
            changes.add(new FileChange(path, before == null ? ChangeAction.NEW : ChangeAction.EDIT, before, content, why));
        }

        void create(String path, String content, String why) {
            if (src.has(path)) {
                throw new ApiException(HttpStatus.CONFLICT, "Já existe um arquivo em " + path + ". Escolha outro nome.");
            }
            put(path, content, why);
        }
    }

    /** Opções enviadas pelo usuário, completadas com os padrões da proposta. */
    private static final class Opts {
        final Map<String, Object> values = new LinkedHashMap<>();

        Opts(Proposal p, Map<String, Object> raw) {
            for (Param param : p.params()) {
                values.put(param.name(), raw != null && raw.containsKey(param.name()) ? raw.get(param.name()) : param.value());
            }
        }

        String text(String name) {
            Object v = values.get(name);
            return v == null ? null : String.valueOf(v).trim();
        }

        boolean bool(String name) {
            Object v = values.get(name);
            return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(String.valueOf(v));
        }

        List<String> list(String name) {
            Object v = values.get(name);
            List<String> out = new ArrayList<>();
            if (v instanceof List<?> l) {
                for (Object x : l) {
                    if (x != null && !String.valueOf(x).isBlank()) {
                        out.add(String.valueOf(x).trim());
                    }
                }
            }
            return out;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows(String name) {
            Object v = values.get(name);
            List<Map<String, Object>> out = new ArrayList<>();
            if (v instanceof List<?> l) {
                for (Object x : l) {
                    if (x instanceof Map<?, ?> m) {
                        out.add((Map<String, Object>) m);
                    }
                }
            }
            return out;
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }

    private void applyResource(Context ctx, Proposal p, Opts o, Out out) {
        Facts facts = ctx.facts();
        String entity = o.text("entity");
        if (!Names.isJavaIdentifier(entity) || !Character.isUpperCase(entity.charAt(0)) || entity.length() > 40) {
            throw invalid("O nome da entidade precisa começar com letra maiúscula e ter só letras e números (ex.: Produto).");
        }
        if (ctx.src().any(".*/" + entity + "\\.java")) {
            throw invalid("Já existe uma classe " + entity + " no projeto. Escolha outro nome.");
        }
        String path = apiPath(o.text("path"));
        List<SpringCode.F> fields = userFields(entity, o.rows("fields"));
        Set<Operation> ops = operations(o.list("operations"));
        String module = entity.toLowerCase(Locale.ROOT);
        String pkg = facts.basePackage + "." + module;
        String dir = facts.basePackageDir + module + "/";
        String table = Names.snake(Names.plural(Names.kebab(entity)));
        boolean jpa = ctx.stack().jpa();
        out.create(dir + entity + ".java", SpringCode.entity(pkg, entity, table, fields, jpa), "Entidade com os campos escolhidos.");
        out.create(dir + entity + "Repository.java", SpringCode.repository(pkg, entity, jpa),
                jpa ? "Acesso ao banco pelo Spring Data JPA." : "Guarda os dados em memória: o projeto não tem banco configurado.");
        if (!ops.isEmpty()) {
            out.create(dir + entity + "Controller.java", SpringCode.controller(pkg, entity + "Controller", entity, path, ops, fields, "Long", Set.of()),
                    "Rotas: " + SpringCode.describe(ops) + ".");
            if (o.bool("tests")) {
                out.create(testDir(facts, dir) + entity + "ControllerTest.java",
                        SpringCode.resourceTest(pkg, entity + "Controller", entity, path, ops, fields, "Long", Set.of()), "Testes da API, sem banco.");
            }
        }
        if (jpa) {
            if (ctx.stack().flyway() && ("mysql".equals(ctx.stack().database()) || "postgresql".equals(ctx.stack().database()))) {
                String migrations = "backend/src/main/resources/db/migration/";
                int n = SpringCode.nextMigration(ctx.src(), migrations);
                out.create(migrations + "V" + n + "__criar_" + table + ".sql", SpringCode.migration(ctx.stack().database(), table, fields),
                        "Cria a tabela " + table + " no banco.");
            } else if (ctx.stack().flyway()) {
                out.notes.add("Crie a migração da tabela " + table + " à mão: o NOVA só escreve SQL para MySQL e PostgreSQL.");
            } else {
                out.notes.add("Sem Flyway, o Hibernate cria a tabela " + table + " ao iniciar (ddl-auto: update).");
            }
        } else {
            out.notes.add("O projeto não tem banco: os dados ficam em memória e somem quando a aplicação reinicia.");
        }
        permitAccess(ctx, o, path, ops, out);
        if (o.bool("page") && "nextjs".equals(ctx.stack().frontend()) && ops.contains(Operation.LIST)) {
            String route = o.text("route");
            if (route == null || !route.matches("/[a-z0-9][a-z0-9/-]{0,80}")) {
                throw invalid("A rota da tela deve começar com / e ter só letras minúsculas, números e hífens.");
            }
            String file = "frontend/src/app" + route + "/page.tsx";
            String existing = ctx.src().get(file);
            if (existing != null && !existing.contains(FrontendReader.PLACEHOLDER)) {
                out.notes.add("A tela " + route + " já foi editada por você; ela não foi alterada. Use " + path + " nela.");
            } else {
                List<SpringCode.F> columns = fields.stream().filter(f -> f.kind() != FieldKind.LONG_TEXT).limit(6).toList();
                out.put(file, SpringCode.nextListPage(route, SpringCode.label(p.part().replace("API · ", "").toLowerCase(Locale.ROOT)), path, entity,
                        columns.isEmpty() ? fields : columns), "A tela passa a listar os dados da API, com mensagens de vazio e de erro.");
            }
        }
        if (fields.stream().anyMatch(SpringCode.F::statusField)) {
            out.notes.add("Os campos de status começam no primeiro valor. Depois de aplicar, a aba Melhorias vai sugerir a rota para mudá-los.");
        }
    }

    private void applyCrud(Context ctx, Proposal p, Opts o, Out out) {
        Entity e = entityOf(ctx, p.where().getFirst());
        Set<Operation> ops = operations(o.list("operations"));
        if (ops.isEmpty()) {
            throw invalid("Escolha ao menos uma operação.");
        }
        String path = apiPath(o.text("path"));
        String pkg = packageOf(ctx.src().get(e.file()));
        String dir = e.file().substring(0, e.file().lastIndexOf('/') + 1);
        String className = ctx.src().any(".*/" + e.name() + "Controller\\.java") ? e.name() + "CrudController" : e.name() + "Controller";
        if (ctx.src().any(".*/" + className + "\\.java")) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe " + className + ".java no projeto.");
        }
        Set<String> imports = new TreeSet<>();
        String repoPkg = packageOf(ctx.src().get(e.repositoryFile()));
        if (repoPkg != null && !repoPkg.equals(pkg)) {
            imports.add(repoPkg + "." + e.repository());
        }
        List<SpringCode.F> fields = existingFields(ctx, e, imports);
        String idType = e.idType() == null ? "Long" : e.idType();
        out.create(dir + className + ".java", SpringCode.controller(pkg, className, e.name(), path, ops, fields, idType, imports),
                "Rotas: " + SpringCode.describe(ops) + ".");
        if (o.bool("tests")) {
            out.create(testDir(ctx.facts(), dir) + className + "Test.java", SpringCode.resourceTest(pkg, className, e.name(), path, ops, fields, idType, imports),
                    "Testes das novas rotas, sem banco.");
        }
        permitAccess(ctx, o, path, ops, out);
    }

    private void applyStatus(Context ctx, Proposal p, Opts o, Out out) {
        Entity e = entityOf(ctx, p.where().getFirst());
        String fieldName = o.text("field");
        Field field = e.fields().stream().filter(f -> f.name().equals(fieldName)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "O campo mudou desde a análise. Recarregue a aba Melhorias."));
        String method = o.text("method");
        if (!List.of("PATCH", "PUT", "POST").contains(method)) {
            throw invalid("Use PATCH, PUT ou POST.");
        }
        String path = o.text("path");
        Matcher vars = Pattern.compile("\\{([a-zA-Z]\\w*)\\}").matcher(path == null ? "" : path);
        List<String> found = new ArrayList<>();
        while (vars.find()) {
            found.add(vars.group(1));
        }
        if (path == null || !path.matches("/[a-zA-Z0-9/{}_-]{1,120}") || found.size() != 1) {
            throw invalid("O caminho precisa começar com / e ter exatamente uma variável, como /api/pedidos/{id}/status.");
        }
        List<String> values = o.list("values").stream().map(v -> v.toUpperCase(Locale.ROOT)).distinct().toList();
        if (values.isEmpty()) {
            throw invalid("Escolha ao menos um valor.");
        }
        for (String v : values) {
            if (!STATUS_VALUE.matcher(v).matches()) {
                throw invalid("Valor inválido: " + v + ". Use letras maiúsculas, números e _.");
            }
            if (field.kind() == FieldKind.ENUM && field.enumValues() != null && !field.enumValues().contains(v)) {
                throw invalid(v + " não existe em " + field.enumType() + ". Valores: " + String.join(", ", field.enumValues()) + ".");
            }
        }
        String pkg = packageOf(ctx.src().get(e.file()));
        String dir = e.file().substring(0, e.file().lastIndexOf('/') + 1);
        String className = e.name() + Character.toUpperCase(field.name().charAt(0)) + field.name().substring(1) + "Controller";
        if (ctx.src().any(".*/" + className + "\\.java")) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe " + className + ".java no projeto.");
        }
        Set<String> imports = new TreeSet<>();
        String repoPkg = packageOf(ctx.src().get(e.repositoryFile()));
        if (repoPkg != null && !repoPkg.equals(pkg)) {
            imports.add(repoPkg + "." + e.repository());
        }
        SpringCode.F f = toF(ctx, e, field, imports);
        String idType = e.idType() == null ? "Long" : e.idType();
        out.create(dir + className + ".java", SpringCode.statusController(pkg, className, e.name(), f, method, path, found.getFirst(), values, idType, imports),
                method + " " + path + " aceita: " + String.join(", ", values) + ".");
        if (o.bool("tests")) {
            out.create(testDir(ctx.facts(), dir) + className + "Test.java",
                    SpringCode.statusTest(pkg, className, e.name(), f, method, path, found.getFirst(), values, field.enumValues(), idType, imports),
                    "Testes: valor aceito, desconhecido, não permitido e item inexistente.");
        }
        if ("PUBLIC".equals(o.text("access"))) {
            permit(ctx, List.<String[]>of(new String[] {method, path.replaceAll("\\{[^}]+\\}", "*")}), out);
        }
    }

    private void applyPage(Context ctx, Proposal p, Opts o, Out out) {
        String file = p.where().getFirst();
        Screen s = ctx.screens().stream().filter(x -> x.file().equals(file)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A tela mudou desde a análise."));
        String key = Names.key(s.route().substring(s.route().lastIndexOf('/') + 1));
        Resource r = ctx.resources().stream().filter(x -> x.entity() != null && x.operations().containsKey(Operation.LIST)
                && (Names.key(x.name()).equals(key) || Names.key(x.entity()).equals(key))).findFirst().orElseThrow();
        Entity e = ctx.facts().entities.stream().filter(x -> x.name().equals(r.entity())).findFirst().orElseThrow();
        List<String> chosen = o.list("columns");
        List<SpringCode.F> columns = new ArrayList<>();
        Set<String> ignored = new TreeSet<>();
        for (Field f : simpleFields(e)) {
            if (chosen.contains(f.name())) {
                columns.add(toF(ctx, e, f, ignored));
            }
        }
        if (columns.isEmpty()) {
            throw invalid("Escolha ao menos uma coluna.");
        }
        String title = o.text("title");
        if (title == null || title.isBlank() || title.length() > 80) {
            throw invalid("Dê um título de até 80 caracteres.");
        }
        out.put(file, SpringCode.nextListPage(s.route(), title, r.operations().get(Operation.LIST).path(), e.name(), columns),
                "Lista os dados de " + r.path() + ", com mensagens de vazio e de erro.");
    }

    private void applyTests(Context ctx, Proposal p, Opts o, Out out) {
        Facts.Controller c = ctx.facts().controllers.stream().filter(x -> x.file().equals(p.where().getFirst())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "O controller mudou desde a análise."));
        List<String> paths = o.list("paths").stream().filter(x -> c.testableGets().stream().anyMatch(e -> e.path().equals(x))).toList();
        if (paths.isEmpty()) {
            throw invalid("Escolha ao menos uma rota.");
        }
        Set<String> imports = new TreeSet<>();
        for (Facts.CtorParam param : c.constructor()) {
            String declared = ctx.src().paths("backend/src/main/java/.*/" + param.type() + "\\.java").stream().findFirst().orElse(null);
            String pkg = declared == null ? null : packageOf(ctx.src().get(declared));
            if (pkg != null && !pkg.equals(c.pkg())) {
                imports.add(pkg + "." + param.type());
            }
        }
        String dir = c.file().substring(0, c.file().lastIndexOf('/') + 1);
        String testName = c.name() + "Test.java";
        String target = testDir(ctx.facts(), dir) + (ctx.src().has(testDir(ctx.facts(), dir) + testName) ? c.name() + "NovaTest.java" : testName);
        String code = SpringCode.getsTest(c.pkg(), c.name(), c.constructor(), paths, imports);
        if (target.endsWith("NovaTest.java")) {
            code = code.replace("class " + c.name() + "Test {", "class " + c.name() + "NovaTest {");
        }
        out.create(target, code, "Confere que cada GET responde sem erro, com as dependências substituídas por dublês.");
    }

    private void applyEnv(Context ctx, Opts o, Out out) {
        StringBuilder add = new StringBuilder();
        Set<String> documented = CommonReader.documented(ctx.src());
        for (Map<String, Object> row : o.rows("variables")) {
            String name = String.valueOf(row.getOrDefault("name", "")).trim();
            boolean include = !(row.get("include") instanceof Boolean b) || b;
            if (!include || documented.contains(name)) {
                continue;
            }
            if (!name.matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw invalid("Nome de variável inválido: " + name);
            }
            String value = row.get("value") == null ? "" : String.valueOf(row.get("value")).replaceAll("[\\r\\n]", "").trim();
            if (!value.isEmpty() && !SecretScanner.scan(".env.example", name + "=" + value).isEmpty()) {
                throw invalid("O valor de exemplo de " + name + " parece uma credencial real. Deixe em branco.");
            }
            add.append(name).append('=').append(value).append('\n');
        }
        if (add.isEmpty()) {
            return;
        }
        String current = ctx.src().get(".env.example");
        String base = current == null ? "# Copie para .env e preencha. Nunca faça commit do .env.\n" : current.endsWith("\n") ? current : current + "\n";
        out.put(".env.example", base + "\n# Adicionadas pela aba Melhorias\n" + add, "Documenta as variáveis que o código lê.");
    }

    private void applySecret(Context ctx, Proposal p, Opts o, Out out) {
        String path = p.where().getFirst();
        String text = ctx.src().get(path);
        String[] lines = text.split("\n", -1);
        Set<String> added = new LinkedHashSet<>();
        for (Param param : p.params()) {
            int line = Integer.parseInt(param.name().substring(4)) - 1;
            String var = o.text(param.name());
            if (var == null || !var.matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw invalid("Nome de variável inválido para a linha " + (line + 1) + ".");
            }
            Matcher m = Pattern.compile("^(\\s*[\\w.\\-\\[\\]]+\\s*[:=]\\s*)(.+?)\\s*$").matcher(lines[line]);
            if (!m.find()) {
                throw new ApiException(HttpStatus.CONFLICT, "Não deu para editar a linha " + (line + 1) + " com segurança. Corrija à mão.");
            }
            lines[line] = m.group(1) + "${" + var + "}";
            added.add(var);
        }
        out.put(path, String.join("\n", lines), "Os valores passam a vir de variáveis de ambiente.");
        Set<String> documented = CommonReader.documented(ctx.src());
        StringBuilder env = new StringBuilder();
        for (String var : added) {
            if (!documented.contains(var)) {
                env.append(var).append("=\n");
            }
        }
        if (!env.isEmpty()) {
            String current = ctx.src().get(".env.example");
            String base = current == null ? "" : current.endsWith("\n") ? current : current + "\n";
            out.put(".env.example", base + "\n# Credenciais tiradas de " + Sources.fileName(path) + "\n" + env, "Documenta as variáveis, sem valores.");
        }
        out.notes.add("Coloque os valores reais no seu .env (que nunca é compartilhado) e troque as credenciais que já estiveram no código.");
    }

    private void applyRegenerated(Context ctx, String feature, List<String> wanted, Out out) {
        ProjectSpec s = ctx.spec();
        List<String> features = new ArrayList<>(s.features() == null ? List.of() : s.features());
        if (!features.contains(feature)) {
            features.add(feature);
        }
        ProjectSpec spec = s.withFeatures(features);
        Map<String, String> generated;
        try {
            generated = generator.generate(spec).files();
        } catch (ApiException e) {
            throw new ApiException(HttpStatus.CONFLICT, "Não deu para gerar os arquivos com a stack deste projeto: " + e.getMessage());
        }
        for (String path : wanted) {
            if (generated.containsKey(path) && !ctx.src().has(path)) {
                out.put(path, generated.get(path), feature.equals("ci") ? "Roda o build e os testes a cada envio." : "Empacota e sobe o app com as dependências.");
            }
        }
        if (feature.equals("docker") && !out.changes.isEmpty()) {
            out.notes.add("Para subir tudo: copie .env.example para .env, preencha e rode docker compose up --build.");
        }
    }

    // =====================================================================================================
    // Apoio
    // =====================================================================================================

    private static void permitAccess(Context ctx, Opts o, String path, Set<Operation> ops, Out out) {
        List<String[]> rules = new ArrayList<>();
        if ("PUBLIC".equals(o.text("readAccess")) && (ops.contains(Operation.LIST) || ops.contains(Operation.READ))) {
            rules.add(new String[] {"GET", path});
            rules.add(new String[] {"GET", path + "/**"});
        }
        if ("PUBLIC".equals(o.text("writeAccess"))) {
            if (ops.contains(Operation.CREATE)) {
                rules.add(new String[] {"POST", path});
            }
            if (ops.contains(Operation.UPDATE)) {
                rules.add(new String[] {"PUT", path + "/**"});
            }
            if (ops.contains(Operation.DELETE)) {
                rules.add(new String[] {"DELETE", path + "/**"});
            }
        }
        if (!rules.isEmpty()) {
            permit(ctx, rules, out);
        }
    }

    private static void permit(Context ctx, List<String[]> rules, Out out) {
        String file = ctx.facts().securityFile;
        if (file == null) {
            return;
        }
        String edited = SpringCode.permit(out.current(file), rules);
        if (edited == null) {
            out.notes.add("Libere as rotas públicas à mão em " + file + ": o formato do arquivo não permite editar com segurança.");
        } else {
            out.put(file, edited, "Libera sem login: " + String.join(", ", rules.stream().map(r -> r[0] + " " + r[1]).toList()) + ".");
        }
    }

    private static String testDir(Facts facts, String mainDir) {
        return mainDir.replace("/src/main/java/", "/src/test/java/");
    }

    static String packageOf(String text) {
        return text == null ? null : Sources.first(Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;"), text, 1);
    }

    private static Entity entityOf(Context ctx, String file) {
        return ctx.facts().entities.stream().filter(e -> e.file().equals(file)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A entidade mudou desde a análise. Recarregue a aba Melhorias."));
    }

    private static List<Field> simpleFields(Entity e) {
        return e.fields().stream().filter(f -> !f.id() && f.kind() != FieldKind.RELATION && f.kind() != FieldKind.COLLECTION
                && f.kind() != FieldKind.OTHER).toList();
    }

    private static List<SpringCode.F> existingFields(Context ctx, Entity e, Set<String> imports) {
        List<SpringCode.F> list = new ArrayList<>();
        for (Field f : simpleFields(e)) {
            if (f.writable()) {
                list.add(toF(ctx, e, f, imports));
            }
        }
        return list;
    }

    private static SpringCode.F toF(Context ctx, Entity e, Field f, Set<String> imports) {
        String enumRef = null;
        if (f.kind() == FieldKind.ENUM) {
            String type = f.enumType();
            String entityText = ctx.src().get(e.file());
            if (entityText != null && entityText.matches("(?s).*\\benum\\s+" + Pattern.quote(type) + "\\b.*") && !f.type().contains(".")) {
                enumRef = e.name() + "." + type;
            } else if (f.type().contains(".")) {
                enumRef = f.type();
            } else {
                enumRef = type;
                String declared = ctx.src().paths("backend/src/main/java/.*/" + type + "\\.java").stream().findFirst().orElse(null);
                String pkg = declared == null ? null : packageOf(ctx.src().get(declared));
                if (pkg != null && !pkg.equals(packageOf(entityText))) {
                    imports.add(pkg + "." + type);
                }
            }
        }
        String javaType = f.kind() == FieldKind.ENUM ? enumRef : f.type().replaceAll("^java\\.\\w+\\.", "");
        boolean primitive = Set.of("int", "long", "double", "float", "boolean", "short").contains(f.type());
        String simpleType = switch (f.kind()) {
            case TEXT, LONG_TEXT -> "String";
            case ENUM -> enumRef;
            default -> primitive ? f.type() : SpringReader.simple(javaType);
        };
        return new SpringCode.F(f.name(), f.kind(), simpleType, f.required(), enumRef, f.enumValues(), false, primitive);
    }

    private static List<SpringCode.F> userFields(String entity, List<Map<String, Object>> rows) {
        if (rows.isEmpty() || rows.size() > 30) {
            throw invalid("A entidade precisa ter de 1 a 30 campos.");
        }
        List<SpringCode.F> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.getOrDefault("name", "")).trim();
            String type = String.valueOf(row.getOrDefault("type", "TEXT")).trim().toUpperCase(Locale.ROOT);
            boolean required = row.get("required") instanceof Boolean b && b;
            if (!FIELD_NAME.matcher(name).matches() || !Names.isJavaIdentifier(name) || name.equals("id")) {
                throw invalid("Nome de campo inválido: \"" + name + "\". Use camelCase, sem acentos (ex.: dataEntrega).");
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw invalid("O campo " + name + " aparece duas vezes.");
            }
            if (!FIELD_TYPES.contains(type)) {
                throw invalid("Tipo inválido para " + name + ": " + type + ".");
            }
            if (type.equals("STATUS")) {
                List<String> values = new ArrayList<>();
                if (row.get("values") instanceof List<?> l) {
                    for (Object v : l) {
                        String value = String.valueOf(v).trim().toUpperCase(Locale.ROOT);
                        if (!STATUS_VALUE.matcher(value).matches()) {
                            throw invalid("Valor inválido em " + name + ": " + v + ". Use maiúsculas, números e _.");
                        }
                        if (!values.contains(value)) {
                            values.add(value);
                        }
                    }
                }
                if (values.size() < 2) {
                    throw invalid("O status " + name + " precisa de ao menos dois valores.");
                }
                String enumRef = entity + "." + Names.pascal(name);
                fields.add(new SpringCode.F(name, FieldKind.ENUM, enumRef, true, enumRef, values, true, false));
            } else {
                FieldKind kind = FieldKind.valueOf(type);
                fields.add(new SpringCode.F(name, kind, SpringCode.javaType(kind), required, null, null, false, false));
            }
        }
        return fields;
    }

    private static Set<Operation> operations(List<String> raw) {
        Set<Operation> ops = EnumSet.noneOf(Operation.class);
        for (String s : raw) {
            try {
                ops.add(Operation.valueOf(s));
            } catch (IllegalArgumentException e) {
                throw invalid("Operação desconhecida: " + s);
            }
        }
        return ops;
    }

    private static String apiPath(String path) {
        if (path == null || !API_PATH.matcher(path).matches() || path.endsWith("/") || path.contains("//")) {
            throw invalid("O caminho deve começar com / e ter só letras minúsculas, números, - e _ (ex.: /api/produtos).");
        }
        return path;
    }

    private static String varName(String[] lines, int index, String path) {
        String line = lines[index];
        if (path.endsWith(".properties")) {
            String key = line.split("[:=]", 2)[0].trim();
            return Names.constant(key.replace('.', '_').replace('-', '_'));
        }
        List<String> keys = new ArrayList<>();
        int indent = line.length() - line.stripLeading().length();
        keys.addFirst(line.trim().split(":", 2)[0]);
        for (int i = index - 1; i >= 0 && indent > 0; i--) {
            String l = lines[i];
            if (l.isBlank() || l.trim().startsWith("#")) {
                continue;
            }
            int ind = l.length() - l.stripLeading().length();
            if (ind < indent && l.trim().endsWith(":")) {
                keys.addFirst(l.trim().replace(":", ""));
                indent = ind;
            }
        }
        return Names.constant(String.join("_", keys).replace('.', '_').replace('-', '_'));
    }

    public static List<Map<String, Object>> defaultFields(String key) {
        List<Map<String, Object>> f = new ArrayList<>();
        if (key.matches(".*(produt|item|servic|plano|curso|livro|prato|cardapio|menu|oferta|imove|veicul).*")) {
            f.add(field("nome", "TEXT", true, null));
            f.add(field("descricao", "LONG_TEXT", false, null));
            f.add(field("preco", "DECIMAL", true, null));
            f.add(field("ativo", "BOOLEAN", false, null));
        } else if (key.matches(".*(pedid|order|compra|venda|reserva|agendament|consulta|assinatura|matricula|entrega).*")) {
            f.add(field("cliente", "TEXT", true, null));
            f.add(field("total", "DECIMAL", false, null));
            f.add(field("status", "STATUS", true, defaultStatuses(key)));
            f.add(field("observacao", "LONG_TEXT", false, null));
        } else if (key.matches(".*(client|usuari|pessoa|paciente|aluno|contato|funcionari|membro|autor|fornecedor).*")) {
            f.add(field("nome", "TEXT", true, null));
            f.add(field("email", "TEXT", true, null));
            f.add(field("telefone", "TEXT", false, null));
        } else if (key.matches(".*(categori|tag|tipo|grupo|departament|setor).*")) {
            f.add(field("nome", "TEXT", true, null));
            f.add(field("descricao", "LONG_TEXT", false, null));
        } else if (key.matches(".*(post|noticia|artigo|tarefa|task|ticket|chamado|projeto|evento).*")) {
            f.add(field("titulo", "TEXT", true, null));
            f.add(field("conteudo", "LONG_TEXT", false, null));
            f.add(field("status", "STATUS", true, defaultStatuses(key)));
        } else {
            f.add(field("nome", "TEXT", true, null));
            f.add(field("descricao", "LONG_TEXT", false, null));
        }
        return f;
    }

    public static List<String> defaultStatuses(String key) {
        if (key.matches(".*(reserva|agendament|consulta|evento).*")) {
            return List.of("AGENDADO", "CONFIRMADO", "CONCLUIDO", "CANCELADO");
        }
        if (key.matches(".*(tarefa|task|ticket|chamado|projeto).*")) {
            return List.of("ABERTO", "EM_ANDAMENTO", "CONCLUIDO", "CANCELADO");
        }
        if (key.matches(".*(post|noticia|artigo).*")) {
            return List.of("RASCUNHO", "PUBLICADO", "ARQUIVADO");
        }
        if (key.matches(".*(assinatura|matricula).*")) {
            return List.of("ATIVA", "SUSPENSA", "CANCELADA");
        }
        return List.of("CRIADO", "PAGO", "ENVIADO", "ENTREGUE", "CANCELADO");
    }

    private static Map<String, Object> field(String name, String type, boolean required, List<String> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("required", required);
        if (values != null) {
            m.put("values", values);
        }
        return m;
    }

    private static List<Option> typeOptions() {
        return List.of(new Option("TEXT", "Texto"), new Option("LONG_TEXT", "Texto longo"), new Option("INTEGER", "Número inteiro"),
                new Option("DECIMAL", "Decimal (valores)"), new Option("BOOLEAN", "Sim ou não"), new Option("DATE", "Data"),
                new Option("DATETIME", "Data e hora"), new Option("STATUS", "Status (lista de valores)"));
    }

    private static List<Option> operationOptions() {
        return List.of(new Option("LIST", "Listar"), new Option("READ", "Ver um item"), new Option("CREATE", "Criar"), new Option("UPDATE", "Editar"),
                new Option("DELETE", "Excluir"));
    }

    private static List<Option> accessOptions() {
        return List.of(new Option("PUBLIC", "Qualquer pessoa"), new Option("AUTHENTICATED", "Só quem fez login"));
    }

    static String id(String kind, String target) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(target.getBytes(StandardCharsets.UTF_8));
            return kind + "-" + HexFormat.of().formatHex(hash).substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
