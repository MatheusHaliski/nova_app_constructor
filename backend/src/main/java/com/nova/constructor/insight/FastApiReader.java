package com.nova.constructor.insight;

import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.Field;
import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Model.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lê um backend FastAPI: rotas (app e APIRouter), dependências de autenticação, modelos SQLAlchemy, esquemas e testes. */
final class FastApiReader {

    private static final Pattern ROUTER = Pattern.compile("(\\w+)\\s*=\\s*APIRouter\\(([^)]*)\\)");
    private static final Pattern PREFIX = Pattern.compile("prefix\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern ROUTE = Pattern.compile("@(\\w+)\\.(get|post|put|patch|delete)\\(\\s*[\"']([^\"']*)[\"']");
    private static final Pattern DEF = Pattern.compile("(?:async\\s+)?def\\s+(\\w+)\\s*\\(");
    private static final Pattern INCLUDE = Pattern.compile("include_router\\(\\s*(\\w+)\\.(\\w+)\\s*(?:,\\s*prefix\\s*=\\s*[\"']([^\"']*)[\"'])?");
    private static final Pattern MODEL = Pattern.compile("(?m)^class\\s+(\\w+)\\(([^)]*)\\):");
    private static final Pattern COLUMN = Pattern.compile("(?m)^\\s+(\\w+)\\s*(?::\\s*Mapped\\[([^\\]]+)\\])?\\s*=\\s*(?:mapped_column|Column|relationship)\\(([^\\n]*)");

    private FastApiReader() {}

    static void read(Sources src, Facts facts) {
        Map<String, String> includePrefix = new HashMap<>();
        for (String p : src.paths("backend/.+\\.py")) {
            Matcher m = INCLUDE.matcher(src.get(p));
            while (m.find()) {
                if (m.group(3) != null) {
                    includePrefix.put(m.group(1), m.group(3));
                }
            }
        }
        facts.securityPresent = src.has("backend/app/auth.py") || src.all().values().stream().anyMatch(t -> t.contains("OAuth2PasswordBearer") || t.contains("HTTPBearer"));
        for (String p : src.paths("backend/.+\\.py")) {
            String text = src.get(p);
            String file = Sources.fileName(p);
            String module = file.replace(".py", "");
            if (p.startsWith("backend/tests/") || file.startsWith("test_")) {
                facts.testCount++;
                facts.testText.add(text);
                facts.items.add(new Facts.Item(Role.TEST, file, p, SpringReader.count(Pattern.compile("(?m)^\\s*(?:async\\s+)?def\\s+test_"), text) + " testes"));
                continue;
            }
            Map<String, String[]> routers = new HashMap<>();
            Matcher r = ROUTER.matcher(text);
            while (r.find()) {
                String prefix = Sources.first(PREFIX, r.group(2), 1);
                routers.put(r.group(1), new String[] {(includePrefix.getOrDefault(module, "")) + (prefix == null ? "" : prefix),
                        r.group(2).contains("Depends(") ? "AUTHENTICATED" : null});
            }
            List<Endpoint> endpoints = new ArrayList<>();
            Matcher route = ROUTE.matcher(text);
            while (route.find()) {
                String owner = route.group(1);
                if (!owner.equals("app") && !routers.containsKey(owner)) {
                    continue;
                }
                String prefix = owner.equals("app") ? "" : routers.get(owner)[0];
                Matcher def = DEF.matcher(text);
                String handler = "";
                String signature = "";
                if (def.find(route.end())) {
                    handler = def.group(1);
                    String params = SpringReader.balanced(text, text.indexOf('(', def.start(1)));
                    signature = params == null ? "" : params;
                }
                String access = !facts.securityPresent ? "PUBLIC"
                        : signature.contains("Depends(") || (!owner.equals("app") && routers.get(owner)[1] != null) ? "AUTHENTICATED" : "PUBLIC";
                endpoints.add(new Endpoint(route.group(2).toUpperCase(Locale.ROOT), SpringReader.joinPath(prefix, route.group(3)), handler, p,
                        Sources.line(text, route.start()), access));
            }
            facts.endpoints.addAll(endpoints);
            boolean model = false;
            Matcher mm = MODEL.matcher(text);
            while (mm.find()) {
                String bases = mm.group(2);
                if (bases.matches(".*\\b(Base|DeclarativeBase|SQLModel)\\b.*") && !bases.contains("BaseModel")
                        && !mm.group(1).equals("Base")) {
                    model = true;
                    Entity e = entity(p, text, mm.group(1), mm.end());
                    facts.entities.add(e);
                    facts.items.add(new Facts.Item(Role.ENTITY, e.name(), p, e.fields().size() + " campos · tabela " + e.table()));
                }
            }
            if (!endpoints.isEmpty()) {
                facts.items.add(new Facts.Item(Role.CONTROLLER, module, p, endpoints.size() + " rotas"));
            } else if (model) {
                continue;
            } else if (file.contains("service")) {
                facts.items.add(new Facts.Item(Role.SERVICE, module, p, "regras do negócio"));
            } else if (file.equals("config.py") || file.equals("db.py") || file.equals("settings.py") || file.equals("auth.py")) {
                facts.items.add(new Facts.Item(Role.CONFIG, module, p, file.equals("db.py") ? "conexão com o banco"
                        : file.equals("auth.py") ? "autenticação por token" : "variáveis de ambiente"));
            } else if (text.contains("(BaseModel)")) {
                facts.items.add(new Facts.Item(Role.OTHER, module, p, "esquemas de entrada e saída"));
            } else if (!file.equals("__init__.py")) {
                facts.items.add(new Facts.Item(Role.OTHER, module, p, "código de apoio"));
            }
        }
    }

    private static Entity entity(String path, String text, String name, int from) {
        int end = text.indexOf("\nclass ", from);
        String body = text.substring(from, end < 0 ? text.length() : end);
        List<Field> fields = new ArrayList<>();
        List<String> relations = new ArrayList<>();
        Matcher c = COLUMN.matcher(body);
        String idType = null;
        while (c.find()) {
            String type = c.group(2) == null ? "" : c.group(2).replace("Optional[", "").replace("]", "");
            String args = c.group(3);
            boolean id = args.contains("primary_key=True");
            FieldKind kind;
            if (body.substring(c.start()).startsWith(c.group(0)) && c.group(0).contains("relationship(")) {
                kind = type.startsWith("list") || type.startsWith("List") ? FieldKind.COLLECTION : FieldKind.RELATION;
                relations.add(type.replaceAll(".*\\[|\"|'", ""));
            } else {
                String t = type.isEmpty() ? args : type;
                kind = t.matches("(?s).*\\b(str|String)\\b.*") ? (t.contains("Text") ? FieldKind.LONG_TEXT : FieldKind.TEXT)
                        : t.matches("(?s).*\\b(int|Integer|BigInteger)\\b.*") ? FieldKind.INTEGER
                        : t.matches("(?s).*\\b(Decimal|Numeric)\\b.*") ? FieldKind.DECIMAL
                        : t.matches("(?s).*\\b(float|Float)\\b.*") ? FieldKind.DOUBLE
                        : t.matches("(?s).*\\b(bool|Boolean)\\b.*") ? FieldKind.BOOLEAN
                        : t.matches("(?s).*\\b(datetime|DateTime)\\b.*") ? FieldKind.DATETIME
                        : t.matches("(?s).*\\b(date|Date)\\b.*") ? FieldKind.DATE : FieldKind.OTHER;
            }
            if (id) {
                idType = kind == FieldKind.TEXT ? "String" : "Integer";
            }
            fields.add(new Field(c.group(1), type.isEmpty() ? kind.name().toLowerCase(Locale.ROOT) : type, kind,
                    args.contains("nullable=False") || id, id, true, null, null));
        }
        String table = Sources.first(Pattern.compile("__tablename__\\s*=\\s*[\"']([^\"']+)"), body, 1);
        return new Entity(name, table == null ? Names.snake(name) : table, path, "fastapi", idType, true, fields, relations, null, null);
    }
}
