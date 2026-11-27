package com.nova.constructor.insight;

import com.nova.constructor.insight.Facts.Controller;
import com.nova.constructor.insight.Facts.CtorParam;
import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.Field;
import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Model.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.AntPathMatcher;

/** Lê um backend Spring Boot: controllers e endpoints, serviços, repositórios, entidades JPA, configuração e testes. */
final class SpringReader {

    private static final Pattern DECL = Pattern.compile("(?m)^[ \\t]*(?:public\\s+|protected\\s+|abstract\\s+|final\\s+|sealed\\s+)*(class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)");
    private static final Pattern METHOD_MAPPING = Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping\\b(\\s*\\(([^)]*)\\))?");
    private static final Pattern STRING = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern NAMED_PATH = Pattern.compile("(?:value|path)\\s*=\\s*\\{?\\s*\"([^\"]*)\"");
    private static final Pattern PRE_AUTHORIZE = Pattern.compile("@PreAuthorize\\s*\\(\\s*\"([^\"]*)\"");
    private static final Pattern REPOSITORY = Pattern.compile(
            "interface\\s+(\\w+)\\s+extends\\s+(?:Jpa|Crud|ListCrud|PagingAndSorting|ListPagingAndSorting)Repository\\s*<\\s*([\\w.]+)\\s*,\\s*([\\w.]+)\\s*>");
    private static final Pattern ENUM = Pattern.compile("\\benum\\s+(\\w+)\\s*(?:implements[^{]*)?\\{([^}]*)\\}");
    private static final Pattern FIELD = Pattern.compile(
            "^(?:@[\\w.]+(?:\\s*\\([^)]*\\))?\\s*)*(?:private|protected|public)\\s+(?:final\\s+)?([\\w.]+(?:\\s*<[^;=]*>)?(?:\\[\\])?)\\s+(\\w+)\\s*(?:=[^;]*)?;");
    private static final Pattern TABLE = Pattern.compile("@Table\\s*\\([^)]*name\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern PERMIT = Pattern.compile("requestMatchers\\s*\\(([^)]*)\\)\\s*\\.permitAll\\(\\)");
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private SpringReader() {}

    private record JavaFile(String path, String text, String pkg, String kind, String name, String head, String body) {}

    private record Permit(String method, String pattern) {}

    static void read(Sources src, Facts facts) {
        List<JavaFile> files = new ArrayList<>();
        for (String path : src.paths("backend/src/main/java/.+\\.java")) {
            String text = src.get(path);
            Matcher decl = DECL.matcher(text);
            if (!decl.find()) {
                continue;
            }
            files.add(new JavaFile(path, text, Sources.first(PACKAGE, text, 1), decl.group(1), decl.group(2),
                    text.substring(0, decl.start()), text.substring(decl.start())));
        }
        List<String> tests = src.paths("backend/src/test/java/.+\\.java");
        facts.testCount += tests.size();
        tests.forEach(p -> facts.testText.add(src.get(p)));
        for (String p : tests) {
            facts.items.add(new Facts.Item(Role.TEST, Sources.fileName(p).replace(".java", ""), p, testDetail(src.get(p))));
        }

        Map<String, List<String>> enums = new HashMap<>();
        for (JavaFile f : files) {
            Matcher m = ENUM.matcher(f.text());
            while (m.find()) {
                enums.put(m.group(1), enumValues(m.group(2)));
            }
        }

        // Segurança: rotas liberadas no SecurityFilterChain.
        List<Permit> permits = new ArrayList<>();
        boolean anyPublic = false;
        for (JavaFile f : files) {
            if (f.text().contains("SecurityFilterChain")) {
                facts.securityFile = f.path();
                facts.securityPresent = true;
                Matcher m = PERMIT.matcher(f.text());
                while (m.find()) {
                    String args = m.group(1);
                    Matcher method = Pattern.compile("HttpMethod\\.(\\w+)").matcher(args);
                    String httpMethod = method.find() ? method.group(1) : null;
                    Matcher s = STRING.matcher(args);
                    while (s.find()) {
                        permits.add(new Permit(httpMethod, s.group(1)));
                    }
                }
                anyPublic |= f.text().matches("(?s).*anyRequest\\(\\)\\s*\\.permitAll\\(\\).*");
            }
        }
        String pom = src.get("backend/pom.xml");
        String gradle = src.get("backend/build.gradle.kts");
        boolean securityDependency = (pom != null && pom.contains("spring-boot-starter-security"))
                || (gradle != null && gradle.contains("spring-boot-starter-security"));
        facts.securityPresent |= securityDependency;
        boolean allPublic = anyPublic || !facts.securityPresent;

        Map<String, String[]> repositories = new HashMap<>();
        for (JavaFile f : files) {
            Matcher m = REPOSITORY.matcher(f.text());
            if (m.find()) {
                repositories.put(simple(m.group(2)), new String[] {m.group(1), f.path(), simple(m.group(3))});
            } else if (f.head().contains("@Repository") && f.name().endsWith("Repository") && f.kind().equals("class")) {
                repositories.put(f.name().substring(0, f.name().length() - "Repository".length()), new String[] {f.name(), f.path(), null});
            }
        }
        Set<String> entityNames = new LinkedHashSet<>();
        for (JavaFile f : files) {
            if (f.head().contains("@Entity")) {
                entityNames.add(f.name());
            }
        }

        for (JavaFile f : files) {
            String head = f.head();
            if (head.contains("@SpringBootApplication")) {
                facts.basePackage = f.pkg();
                facts.basePackageDir = f.path().substring(0, f.path().lastIndexOf('/') + 1);
                facts.items.add(new Facts.Item(Role.CONFIG, f.name(), f.path(), "ponto de entrada da aplicação"));
            } else if (head.contains("@RestController") || head.contains("@Controller")) {
                Controller c = controller(f, permits, allPublic);
                facts.controllers.add(c);
                facts.endpoints.addAll(c.endpoints());
                facts.items.add(new Facts.Item(Role.CONTROLLER, f.name(), f.path(), endpointsDetail(c.endpoints(), f.text())));
            } else if (head.contains("@Service")) {
                facts.items.add(new Facts.Item(Role.SERVICE, f.name(), f.path(), methodCount(f.body()) + " métodos públicos"
                        + (f.body().contains("@Transactional") ? " · usa transações" : "")));
            } else if (repositories.values().stream().anyMatch(r -> r[1].equals(f.path()))) {
                String entity = repositories.entrySet().stream().filter(e -> e.getValue()[1].equals(f.path())).map(Map.Entry::getKey).findFirst().orElse("");
                int queries = count(Pattern.compile("@Query|\\b(?:find|count|exists|delete)By\\w+\\s*\\("), f.body());
                facts.items.add(new Facts.Item(Role.REPOSITORY, f.name(), f.path(), "dados de " + entity
                        + (queries == 0 ? " · só as operações padrão" : " · " + queries + " consultas próprias")));
            } else if (head.contains("@Entity")) {
                Entity e = entity(f, enums, entityNames, repositories);
                facts.entities.add(e);
                facts.items.add(new Facts.Item(Role.ENTITY, f.name(), f.path(), e.fields().size() + " campos · tabela " + e.table()));
            } else if (head.contains("@Configuration") || head.contains("@ConfigurationProperties") || f.name().endsWith("Config")) {
                facts.items.add(new Facts.Item(Role.CONFIG, f.name(), f.path(), configDetail(f.text())));
            } else {
                facts.items.add(new Facts.Item(Role.OTHER, f.name(), f.path(), f.kind()));
            }
        }
        for (String p : src.paths("backend/src/main/resources/application[^/]*\\.(yml|yaml|properties)")) {
            facts.items.add(new Facts.Item(Role.CONFIG, Sources.fileName(p), p, "propriedades da aplicação"));
        }

        // Quem altera o status de cada entidade (fora a própria entidade).
        for (Entity e : facts.entities) {
            for (Field field : e.fields()) {
                if (!isStatusName(field.name())) {
                    continue;
                }
                String setter = "set" + Character.toUpperCase(field.name().charAt(0)) + field.name().substring(1) + "(";
                Set<String> writers = new LinkedHashSet<>();
                for (JavaFile f : files) {
                    if (!f.path().equals(e.file()) && f.text().contains("." + setter)) {
                        writers.add(f.path());
                    }
                }
                facts.statusWriters.put(e.name(), writers);
            }
        }
    }

    static boolean isStatusName(String name) {
        String n = Names.normalize(name);
        return n.equals("status") || n.equals("situacao") || n.equals("estado") || n.equals("state") || n.equals("etapa") || n.endsWith("status");
    }

    private static Controller controller(JavaFile f, List<Permit> permits, boolean allPublic) {
        String base = "";
        Matcher cm = CLASS_MAPPING.matcher(f.head());
        if (cm.find()) {
            base = mappingPath(cm.group(1));
        }
        String classAuth = Sources.first(PRE_AUTHORIZE, f.head(), 1);
        List<Endpoint> endpoints = new ArrayList<>();
        List<Endpoint> testable = new ArrayList<>();
        String text = f.text();
        int offset = f.head().length();
        Matcher m = METHOD_MAPPING.matcher(f.body());
        while (m.find()) {
            String method = m.group(1).toUpperCase(Locale.ROOT);
            String sub = m.group(3) == null ? "" : mappingPath(m.group(3));
            String path = joinPath(base, sub);
            int nameAt = methodName(f.body(), m.end());
            if (nameAt < 0) {
                continue;
            }
            String handler = identifierAt(f.body(), nameAt);
            String params = balanced(f.body(), f.body().indexOf('(', nameAt));
            int segmentStart = Math.max(f.body().lastIndexOf(';', m.start()), f.body().lastIndexOf('}', m.start()));
            String segment = f.body().substring(Math.max(segmentStart, 0), nameAt);
            String auth = Sources.first(PRE_AUTHORIZE, segment, 1);
            if (auth == null) {
                auth = classAuth;
            }
            String access = auth != null ? accessOf(auth) : (allPublic || permitted(permits, method, path) ? "PUBLIC" : "AUTHENTICATED");
            Endpoint e = new Endpoint(method, path, handler, f.path(), Sources.line(text, offset + m.start()), access);
            endpoints.add(e);
            if (method.equals("GET") && !path.contains("{") && params != null && params.isBlank()) {
                testable.add(e);
            }
        }
        return new Controller(f.name(), f.path(), f.pkg(), base, constructor(f), endpoints, testable);
    }

    /** Parâmetros do construtor; lista vazia para o construtor padrão; null se a injeção for em campos. */
    private static List<CtorParam> constructor(JavaFile f) {
        String body = f.body();
        if (body.contains("@Autowired") && !Pattern.compile("@Autowired\\s+(?:public\\s+)?" + f.name() + "\\s*\\(").matcher(body).find()
                && Pattern.compile("@Autowired\\s+(?:private|protected)").matcher(body).find()) {
            return null;
        }
        Matcher c = Pattern.compile("(?:public\\s+|protected\\s+)?\\b" + f.name() + "\\s*\\(").matcher(body);
        while (c.find()) {
            int open = body.indexOf('(', c.start() + f.name().length());
            String params = balanced(body, open);
            if (params == null) {
                return null;
            }
            int after = open + params.length() + 2;
            String rest = body.substring(Math.min(after, body.length())).stripLeading();
            if (!rest.startsWith("{")) {
                continue;
            }
            List<CtorParam> list = new ArrayList<>();
            for (String part : splitParams(params)) {
                String p = part.replaceAll("@\\w+(?:\\([^)]*\\))?", "").replace("final ", "").trim();
                int space = p.lastIndexOf(' ');
                if (space < 0) {
                    return null;
                }
                list.add(new CtorParam(p.substring(0, space).trim(), p.substring(space + 1).trim()));
            }
            return list;
        }
        if (f.head().contains("@RequiredArgsConstructor") || f.head().contains("@AllArgsConstructor")) {
            List<CtorParam> list = new ArrayList<>();
            Matcher m = Pattern.compile("(?m)^\\s*private\\s+final\\s+([\\w.<>, ?]+?)\\s+(\\w+)\\s*;").matcher(body);
            while (m.find()) {
                list.add(new CtorParam(m.group(1), m.group(2)));
            }
            return list;
        }
        return List.of();
    }

    private static Entity entity(JavaFile f, Map<String, List<String>> enums, Set<String> entityNames, Map<String, String[]> repositories) {
        boolean lombokSetters = f.head().contains("@Data") || f.head().contains("@Setter");
        List<Field> fields = new ArrayList<>();
        List<String> relations = new ArrayList<>();
        String idType = null;
        StringBuilder pending = new StringBuilder();
        int depth = 0;
        for (String raw : f.body().split("\n")) {
            String line = raw.trim();
            int before = depth;
            depth += count(Pattern.compile("\\{"), raw) - count(Pattern.compile("\\}"), raw);
            if (before != 1) {
                pending.setLength(0);
                continue;
            }
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                continue;
            }
            if (line.startsWith("@") && !line.contains(";")) {
                pending.append(' ').append(line);
                continue;
            }
            String annotations = (pending + " " + line).trim();
            pending.setLength(0);
            if (annotations.matches("(?s).*\\bstatic\\b.*")) {
                continue;
            }
            Matcher m = FIELD.matcher(annotations);
            if (!m.find()) {
                continue;
            }
            String type = m.group(1).replaceAll("\\s+", "");
            String name = m.group(2);
            boolean isId = annotations.contains("@Id") || annotations.contains("@EmbeddedId");
            String simpleType = simple(type.replaceAll("<.*", ""));
            FieldKind kind = kindOf(type, annotations, enums, entityNames);
            String enumType = kind == FieldKind.ENUM ? simple(type) : null;
            boolean required = annotations.matches("(?s).*nullable\\s*=\\s*false.*") || annotations.contains("@NotNull")
                    || annotations.contains("@NotBlank") || annotations.contains("@NotEmpty");
            String setter = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "(";
            boolean writable = lombokSetters || line.startsWith("public") || f.body().contains(" " + setter);
            if (isId) {
                idType = wrapper(simpleType);
            }
            if (kind == FieldKind.RELATION || kind == FieldKind.COLLECTION) {
                String target = type.contains("<") ? simple(type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'))) : simpleType;
                relations.add(target);
            }
            fields.add(new Field(name, type, kind, required, isId, writable, enumType, enumType == null ? null : enums.get(enumType)));
        }
        String table = Sources.first(TABLE, f.head(), 1);
        String[] repo = repositories.get(f.name());
        boolean writable = fields.stream().filter(x -> !x.id() && x.kind() != FieldKind.COLLECTION).allMatch(Field::writable);
        return new Entity(f.name(), table == null ? Names.snake(f.name()) : table, f.path(), "spring-boot",
                idType == null && repo != null ? repo[2] : idType, writable, fields, relations,
                repo == null ? null : repo[0], repo == null ? null : repo[1]);
    }

    static FieldKind kindOf(String type, String annotations, Map<String, List<String>> enums, Set<String> entityNames) {
        String base = simple(type.replaceAll("<.*", ""));
        if (type.matches("(?:java\\.util\\.)?(List|Set|Collection)<.*")) {
            return FieldKind.COLLECTION;
        }
        return switch (base) {
            case "String" -> annotations.contains("@Lob") || annotations.matches("(?s).*length\\s*=\\s*(\\d{4,}|[3-9]\\d\\d).*")
                    || annotations.contains("TEXT") ? FieldKind.LONG_TEXT : FieldKind.TEXT;
            case "Integer", "int", "Short", "short" -> FieldKind.INTEGER;
            case "Long", "long" -> FieldKind.LONG;
            case "BigDecimal" -> FieldKind.DECIMAL;
            case "Double", "double", "Float", "float" -> FieldKind.DOUBLE;
            case "Boolean", "boolean" -> FieldKind.BOOLEAN;
            case "LocalDate" -> FieldKind.DATE;
            case "LocalDateTime" -> FieldKind.DATETIME;
            case "Instant", "OffsetDateTime", "ZonedDateTime" -> FieldKind.INSTANT;
            case "UUID" -> FieldKind.UUID;
            default -> enums.containsKey(simple(type)) || annotations.contains("@Enumerated") ? FieldKind.ENUM
                    : entityNames.contains(base) || annotations.matches("(?s).*@(ManyToOne|OneToOne)\\b.*") ? FieldKind.RELATION : FieldKind.OTHER;
        };
    }

    private static String wrapper(String type) {
        return switch (type) {
            case "long" -> "Long";
            case "int" -> "Integer";
            default -> type;
        };
    }

    static String simple(String type) {
        String t = type.trim();
        return t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t;
    }

    private static List<String> enumValues(String body) {
        String constants = body.contains(";") ? body.substring(0, body.indexOf(';')) : body;
        List<String> values = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char ch : constants.toCharArray()) {
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                addConstant(values, current.toString());
                current.setLength(0);
                continue;
            }
            if (depth == 0 && ch != ')') {
                current.append(ch);
            }
        }
        addConstant(values, current.toString());
        return values;
    }

    private static void addConstant(List<String> values, String raw) {
        String v = raw.replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "").replaceAll("@\\w+", "").trim();
        if (v.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            values.add(v);
        }
    }

    private static String mappingPath(String args) {
        Matcher named = NAMED_PATH.matcher(args);
        if (named.find()) {
            return named.group(1);
        }
        String trimmed = args.trim();
        if (trimmed.startsWith("\"") || trimmed.startsWith("{")) {
            Matcher s = STRING.matcher(trimmed);
            return s.find() ? s.group(1) : "";
        }
        return "";
    }

    static String joinPath(String base, String sub) {
        String path = ("/" + base + "/" + sub).replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.replaceAll("\\{(\\w+):[^}]*\\}", "{$1}");
    }

    /** Posição do nome do método que vem depois de uma anotação de mapeamento. */
    private static int methodName(String body, int from) {
        Matcher m = Pattern.compile("(@?)(\\w+)\\s*\\(").matcher(body);
        int pos = from;
        while (m.find(pos)) {
            if (m.group(1).isEmpty() && !Set.of("if", "for", "while", "switch", "return", "new").contains(m.group(2))) {
                return m.start(2);
            }
            int open = body.indexOf('(', m.start(2));
            String inside = balanced(body, open);
            if (inside == null) {
                return -1;
            }
            pos = open + inside.length() + 2;
        }
        return -1;
    }

    private static String identifierAt(String text, int at) {
        Matcher m = Pattern.compile("\\w+").matcher(text);
        return m.find(at) ? m.group() : "";
    }

    /** Conteúdo entre o parêntese aberto em {@code open} e o fechamento correspondente. */
    static String balanced(String text, int open) {
        if (open < 0 || open >= text.length() || text.charAt(open) != '(') {
            return null;
        }
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return text.substring(open + 1, i);
                }
            }
        }
        return null;
    }

    private static List<String> splitParams(String params) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : params.toCharArray()) {
            if (c == '<' || c == '(') {
                depth++;
            } else if (c == '>' || c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static boolean permitted(List<Permit> permits, String method, String path) {
        String concrete = path.replaceAll("\\{[^}]+\\}", "1");
        return permits.stream().anyMatch(p -> (p.method() == null || p.method().equalsIgnoreCase(method))
                && (MATCHER.match(p.pattern(), path) || MATCHER.match(p.pattern(), concrete)));
    }

    private static String accessOf(String expression) {
        Matcher role = Pattern.compile("has(?:Any)?(?:Role|Authority)\\s*\\(\\s*'([^']+)'").matcher(expression);
        if (role.find()) {
            return role.group(1).replace("ROLE_", "");
        }
        if (expression.contains("permitAll")) {
            return "PUBLIC";
        }
        return "AUTHENTICATED";
    }

    private static String endpointsDetail(List<Endpoint> endpoints, String text) {
        String services = String.join(", ", findAll(Pattern.compile("private\\s+final\\s+(\\w+(?:Service|Repository))\\s+\\w+"), text));
        return endpoints.size() + (endpoints.size() == 1 ? " endpoint" : " endpoints") + (services.isEmpty() ? "" : " · usa " + services);
    }

    private static String configDetail(String text) {
        List<String> parts = new ArrayList<>();
        if (text.contains("SecurityFilterChain")) {
            parts.add("segurança e rotas públicas");
        }
        if (text.contains("Jwt")) {
            parts.add("token JWT");
        }
        if (text.contains("Cors")) {
            parts.add("CORS");
        }
        int beans = count(Pattern.compile("@Bean\\b"), text);
        if (beans > 0) {
            parts.add(beans + (beans == 1 ? " bean" : " beans"));
        }
        return parts.isEmpty() ? "configuração" : String.join(" · ", parts);
    }

    private static String testDetail(String text) {
        int tests = count(Pattern.compile("@Test\\b"), text);
        return tests + (tests == 1 ? " teste" : " testes");
    }

    private static int methodCount(String body) {
        return count(Pattern.compile("(?m)^\\s+public\\s+(?!class|interface|enum|record)[\\w<>\\[\\], .?]+\\s+\\w+\\s*\\("), body);
    }

    static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static List<String> findAll(Pattern p, String text) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            if (!out.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }
}
