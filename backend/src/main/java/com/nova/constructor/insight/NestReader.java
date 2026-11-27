package com.nova.constructor.insight;

import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.Field;
import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Model.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lê um backend NestJS: controllers e rotas, serviços, módulos, entidades TypeORM e testes. */
final class NestReader {

    private static final Pattern CONTROLLER = Pattern.compile("@Controller\\(\\s*(?:['\"`]([^'\"`]*)['\"`])?\\s*\\)");
    private static final Pattern ROUTE = Pattern.compile("@(Get|Post|Put|Patch|Delete)\\(\\s*(?:['\"`]([^'\"`]*)['\"`])?\\s*\\)");
    private static final Pattern METHOD = Pattern.compile("\\s*(?:@\\w+\\([^)]*\\)\\s*)*(?:public\\s+|private\\s+)?(?:async\\s+)?(\\w+)\\s*\\(");
    private static final Pattern PREFIX = Pattern.compile("setGlobalPrefix\\(\\s*['\"`]([^'\"`]+)");
    private static final Pattern CLASS = Pattern.compile("export\\s+class\\s+(\\w+)");
    private static final Pattern COLUMN = Pattern.compile(
            "@(PrimaryGeneratedColumn|PrimaryColumn|Column|CreateDateColumn|UpdateDateColumn|ManyToOne|OneToMany|OneToOne|ManyToMany)\\(([^)]*(?:\\([^)]*\\)[^)]*)*)\\)\\s*(?:@\\w+\\([^)]*\\)\\s*)*(\\w+)[!?]?\\s*:\\s*([\\w\\[\\]<>| ]+)");

    private NestReader() {}

    static void read(Sources src, Facts facts) {
        String main = src.get("backend/src/main.ts");
        String prefix = main == null ? "" : java.util.Objects.requireNonNullElse(Sources.first(PREFIX, main, 1), "");
        boolean guard = src.all().entrySet().stream().anyMatch(e -> e.getKey().startsWith("backend/src/") && e.getValue().contains("APP_GUARD"));
        facts.securityPresent = guard;
        List<String> entityNames = new ArrayList<>();
        for (String p : src.paths("backend/src/.+\\.ts")) {
            if (src.get(p).contains("@Entity(")) {
                String name = Sources.first(CLASS, src.get(p), 1);
                if (name != null) {
                    entityNames.add(name);
                }
            }
        }
        for (String p : src.paths("backend/src/.+\\.ts")) {
            String text = src.get(p);
            String name = Sources.first(CLASS, text, 1);
            String file = Sources.fileName(p);
            if (p.endsWith(".spec.ts") || p.endsWith(".test.ts")) {
                facts.testCount++;
                facts.testText.add(text);
                facts.items.add(new Facts.Item(Role.TEST, file, p, SpringReader.count(Pattern.compile("\\bit\\(|\\btest\\("), text) + " testes"));
            } else if (text.contains("@Controller(")) {
                Matcher c = CONTROLLER.matcher(text);
                String base = c.find() && c.group(1) != null ? c.group(1) : "";
                boolean classPublic = c.reset().find() && text.substring(0, c.start()).contains("@Public()");
                List<Endpoint> endpoints = new ArrayList<>();
                Matcher r = ROUTE.matcher(text);
                int last = 0;
                while (r.find()) {
                    Matcher m = METHOD.matcher(text);
                    boolean found = m.find(r.end());
                    String handler = found ? m.group(1) : "";
                    int stop = found ? m.end() : r.end();
                    String segment = text.substring(Math.min(last, r.start()), stop);
                    last = stop;
                    boolean isPublic = classPublic || segment.contains("@Public()");
                    String path = SpringReader.joinPath(prefix, SpringReader.joinPath(base, r.group(2) == null ? "" : r.group(2)))
                            .replaceAll(":(\\w+)", "{$1}");
                    endpoints.add(new Endpoint(r.group(1).toUpperCase(Locale.ROOT), path, handler, p, Sources.line(text, r.start()),
                            !guard || isPublic ? "PUBLIC" : "AUTHENTICATED"));
                }
                facts.endpoints.addAll(endpoints);
                facts.items.add(new Facts.Item(Role.CONTROLLER, name == null ? file : name, p, endpoints.size() + " rotas"));
            } else if (text.contains("@Entity(")) {
                Entity e = entity(p, text, name, entityNames);
                facts.entities.add(e);
                facts.items.add(new Facts.Item(Role.ENTITY, e.name(), p, e.fields().size() + " campos · tabela " + e.table()));
            } else if (text.contains("@Injectable(") && !text.contains("CanActivate")) {
                facts.items.add(new Facts.Item(Role.SERVICE, name == null ? file : name, p, "serviço injetável"));
            } else if (file.endsWith(".module.ts") || file.equals("main.ts") || file.contains("config") || file.endsWith(".guard.ts")) {
                facts.items.add(new Facts.Item(Role.CONFIG, name == null ? file : name, p, file.endsWith(".guard.ts") ? "proteção das rotas"
                        : file.endsWith(".module.ts") ? "módulo" : "inicialização e configuração"));
            } else {
                facts.items.add(new Facts.Item(Role.OTHER, name == null ? file : name, p, "código de apoio"));
            }
        }
    }

    private static Entity entity(String path, String text, String name, List<String> entityNames) {
        List<Field> fields = new ArrayList<>();
        List<String> relations = new ArrayList<>();
        String idType = null;
        Matcher m = COLUMN.matcher(text);
        while (m.find()) {
            String decorator = m.group(1);
            String field = m.group(3);
            String type = m.group(4).trim();
            boolean id = decorator.startsWith("Primary");
            FieldKind kind;
            if (decorator.endsWith("ToMany")) {
                kind = FieldKind.COLLECTION;
            } else if (decorator.endsWith("ToOne")) {
                kind = FieldKind.RELATION;
            } else {
                kind = switch (type.replace("[]", "")) {
                    case "string" -> m.group(2).contains("text") ? FieldKind.LONG_TEXT : FieldKind.TEXT;
                    case "number" -> m.group(2).contains("decimal") || m.group(2).contains("numeric") ? FieldKind.DECIMAL : FieldKind.INTEGER;
                    case "boolean" -> FieldKind.BOOLEAN;
                    case "Date" -> FieldKind.DATETIME;
                    default -> entityNames.contains(type) ? FieldKind.RELATION : FieldKind.OTHER;
                };
            }
            if (id) {
                idType = type.equals("string") ? "String" : "Long";
            }
            if (kind == FieldKind.RELATION || kind == FieldKind.COLLECTION) {
                relations.add(type.replace("[]", ""));
            }
            boolean required = !m.group(2).contains("nullable: true") && !text.contains(field + "?:");
            fields.add(new Field(field, type, kind, required, id, true, null, null));
        }
        String table = Sources.first(Pattern.compile("@Entity\\(\\s*['\"`]([^'\"`]+)"), text, 1);
        String entityName = name == null ? Sources.fileName(path) : name;
        return new Entity(entityName, table == null ? Names.snake(entityName) : table, path, "nestjs", idType, true, fields, relations, null, null);
    }
}
