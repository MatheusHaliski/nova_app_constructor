package com.nova.constructor.generator;

import com.nova.constructor.common.ApiException;
import com.nova.constructor.generator.ProjectSpec.EntitySpec;
import com.nova.constructor.generator.ProjectSpec.FieldSpec;
import com.nova.constructor.generator.ProjectSpec.Requirement;
import com.nova.constructor.generator.ProjectSpec.ScreenSpec;
import com.nova.constructor.generator.ProjectSpec.Theme;
import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Names;
import com.nova.constructor.insight.SpringCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Regras dos dados do domínio no formulário: entidades e campos, caracterização das telas, requisitos e tema.
 * Normaliza o que veio do formulário (ou do Copilot) e recusa o que geraria código inválido.
 */
public final class DomainRules {

    public static final List<String> FIELD_TYPES = List.of("TEXT", "LONG_TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME", "STATUS");
    public static final List<String> TEMPLATES = List.of("LIST", "DETAIL", "FORM", "DASHBOARD", "LOGIN", "SIGNUP", "LANDING", "SETTINGS", "BLANK");
    public static final List<String> FONTS = List.of("system", "Inter", "Roboto", "Montserrat", "Lora");
    private static final Set<String> RESERVED = Set.of("Status", "Auth", "Application", "App", "Security", "Ai", "Object", "String", "Integer",
            "Long", "Boolean", "List", "Map", "Set", "Optional", "Entity", "Repository", "Controller", "Record", "Class", "Override", "Test");
    private static final Set<String> HOME = Set.of("inicio", "home", "pagina-inicial");

    private DomainRules() {}

    /** Resultado da normalização. {@code screens} continua sendo a lista de nomes, como nos projetos antigos. */
    public record Domain(List<EntitySpec> entities, List<String> screens, List<ScreenSpec> screenSpecs, List<Requirement> requirements, Theme theme) {}

    public static Domain normalize(ProjectSpec input, boolean frontend, boolean auth, String backendId, List<String> warnings) {
        List<EntitySpec> entities = entities(input.entitiesOrEmpty());
        if (!entities.isEmpty() && !"spring-boot".equals(backendId)) {
            warnings.add("Hoje o NOVA escreve o código das entidades só no Spring Boot. Neste backend elas ficam registradas no projeto "
                    + "e aparecem como propostas na aba Melhorias.");
        }
        List<ScreenSpec> specs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (frontend) {
            Map<String, ScreenSpec> bySlug = new LinkedHashMap<>();
            Map<String, ScreenSpec> given = new LinkedHashMap<>();
            for (ScreenSpec s : input.screenSpecsOrEmpty()) {
                if (s != null && s.name() != null && !s.name().isBlank()) {
                    given.putIfAbsent(s.name().trim(), s);
                }
            }
            List<String> order = new ArrayList<>(given.keySet());
            for (String n : input.screensOrEmpty()) {
                if (n != null && !n.isBlank() && !order.contains(n.trim())) {
                    order.add(n.trim());
                }
            }
            Set<String> entityNames = new LinkedHashSet<>(entities.stream().map(EntitySpec::name).toList());
            for (String name : order) {
                String slug = PlanService.slug(name);
                if (HOME.contains(slug)) {
                    warnings.add("A tela \"" + name + "\" é a página inicial (/), que todo projeto já tem.");
                    continue;
                }
                if (bySlug.containsKey(slug)) {
                    warnings.add("As telas \"" + bySlug.get(slug).name() + "\" e \"" + name + "\" teriam a mesma rota (/" + slug + "); ficou só a primeira.");
                    continue;
                }
                bySlug.put(slug, screen(name, given.get(name), entityNames, auth, warnings));
            }
            specs.addAll(bySlug.values());
            specs.forEach(s -> names.add(s.name()));
        }
        List<Requirement> requirements = new ArrayList<>();
        for (Requirement r : input.requirementsOrEmpty()) {
            if (r == null || r.text() == null || r.text().isBlank()) {
                continue;
            }
            String priority = r.priority() == null ? "IMPORTANT" : r.priority().toUpperCase(Locale.ROOT);
            if (!List.of("ESSENTIAL", "IMPORTANT", "LATER").contains(priority)) {
                priority = "IMPORTANT";
            }
            requirements.add(new Requirement(r.text().trim(), blankToNull(r.acceptance()), priority));
        }
        return new Domain(entities, names, specs, requirements, frontend ? theme(input.theme()) : null);
    }

    private static List<EntitySpec> entities(List<EntitySpec> raw) {
        List<EntitySpec> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (EntitySpec e : raw) {
            if (e == null || e.name() == null || e.name().isBlank()) {
                continue;
            }
            String name = Names.isJavaIdentifier(e.name().trim()) && Character.isUpperCase(e.name().trim().charAt(0)) ? e.name().trim() : Names.pascal(e.name());
            if (!Names.isJavaIdentifier(name) || name.length() > 40) {
                throw invalid("Nome de entidade inválido: \"" + e.name() + "\".");
            }
            if (RESERVED.contains(name)) {
                throw invalid("\"" + name + "\" é um nome reservado no código gerado. Use outro (ex.: " + name + "Item).");
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw invalid("A entidade " + name + " aparece duas vezes.");
            }
            List<FieldSpec> fields = new ArrayList<>();
            Set<String> fieldNames = new LinkedHashSet<>();
            for (FieldSpec f : e.fields() == null ? List.<FieldSpec>of() : e.fields()) {
                if (f == null || f.name() == null || f.name().isBlank()) {
                    continue;
                }
                String fname = Names.isJavaIdentifier(f.name().trim()) && Character.isLowerCase(f.name().trim().charAt(0)) ? f.name().trim() : Names.camel(f.name());
                if (!Names.isJavaIdentifier(fname) || fname.equals("id") || fname.length() > 40) {
                    throw invalid("Campo inválido em " + name + ": \"" + f.name() + "\".");
                }
                if (!fieldNames.add(fname.toLowerCase(Locale.ROOT))) {
                    throw invalid("O campo " + fname + " aparece duas vezes em " + name + ".");
                }
                String type = f.type() == null ? "TEXT" : f.type().toUpperCase(Locale.ROOT);
                if (!FIELD_TYPES.contains(type)) {
                    throw invalid("Tipo inválido para " + name + "." + fname + ": " + f.type() + ".");
                }
                List<String> values = null;
                if (type.equals("STATUS")) {
                    values = new ArrayList<>();
                    for (String v : f.values() == null ? List.<String>of() : f.values()) {
                        String c = Names.constant(v == null ? "" : v);
                        if (!c.isEmpty() && !values.contains(c)) {
                            values.add(c);
                        }
                    }
                    if (values.size() < 2) {
                        throw invalid("O status " + name + "." + fname + " precisa de ao menos dois valores.");
                    }
                }
                fields.add(new FieldSpec(fname, type, type.equals("STATUS") || Boolean.TRUE.equals(f.required()), values));
            }
            if (fields.isEmpty()) {
                throw invalid("A entidade " + name + " precisa de ao menos um campo.");
            }
            out.add(new EntitySpec(name, fields));
        }
        return out;
    }

    private static ScreenSpec screen(String name, ScreenSpec given, Set<String> entities, boolean auth, List<String> warnings) {
        String template = given == null || given.template() == null ? "BLANK" : given.template().toUpperCase(Locale.ROOT);
        if (!TEMPLATES.contains(template)) {
            template = "BLANK";
        }
        String entity = given == null ? null : blankToNull(given.entity());
        if (entity != null) {
            String wanted = entity;
            String match = entities.stream().filter(e -> e.equalsIgnoreCase(wanted) || e.equals(Names.pascal(wanted))).findFirst().orElse(null);
            if (match == null) {
                throw invalid("A tela \"" + name + "\" usa a entidade " + wanted + ", que não está na lista de dados.");
            }
            entity = match;
        }
        if (List.of("LIST", "DETAIL", "FORM").contains(template) && entity == null) {
            warnings.add("A tela \"" + name + "\" é do tipo " + template + " mas não tem entidade; ela será gerada em branco.");
            template = "BLANK";
        }
        String access = given == null || given.access() == null ? (auth && List.of("SETTINGS", "DASHBOARD").contains(template) ? "AUTH" : "PUBLIC")
                : given.access().toUpperCase(Locale.ROOT);
        if (!List.of("PUBLIC", "AUTH").contains(access)) {
            access = "PUBLIC";
        }
        if (access.equals("AUTH") && !auth) {
            warnings.add("A tela \"" + name + "\" pede login, mas a autenticação não está ativa; ela ficará pública.");
            access = "PUBLIC";
        }
        boolean inMenu = given == null || given.inMenu() == null ? !List.of("DETAIL", "LOGIN", "SIGNUP").contains(template) : given.inMenu();
        return new ScreenSpec(name, template, entity, access, inMenu, given == null ? null : blankToNull(given.purpose()));
    }

    private static Theme theme(Theme t) {
        String primary = t == null || t.primary() == null || !t.primary().matches("#[0-9a-fA-F]{6}") ? "#1554F0" : t.primary().toUpperCase(Locale.ROOT);
        String font = t == null || !FONTS.contains(t.font()) ? "system" : t.font();
        String radius = t == null || t.radius() == null || !List.of("NONE", "SM", "MD", "LG").contains(t.radius().toUpperCase(Locale.ROOT)) ? "MD"
                : t.radius().toUpperCase(Locale.ROOT);
        String density = t == null || !"COMPACT".equalsIgnoreCase(t.density()) ? "COMFORTABLE" : "COMPACT";
        return new Theme(primary, font, radius, density);
    }

    /** Campos da entidade no formato dos modelos de código. Status viram enum aninhado na entidade. */
    public static List<SpringCode.F> fields(EntitySpec e) {
        List<SpringCode.F> out = new ArrayList<>();
        for (FieldSpec f : e.fields()) {
            if (f.type().equals("STATUS")) {
                String ref = e.name() + "." + Names.pascal(f.name());
                out.add(new SpringCode.F(f.name(), FieldKind.ENUM, ref, true, ref, f.values(), true, false));
            } else {
                FieldKind kind = FieldKind.valueOf(f.type());
                out.add(new SpringCode.F(f.name(), kind, SpringCode.javaType(kind), Boolean.TRUE.equals(f.required()), null, null, false, false));
            }
        }
        return out;
    }

    /** Caminho da API de uma entidade (ex.: Pedido → /api/pedidos). */
    public static String apiPath(String entity) {
        return "/api/" + Names.plural(Names.kebab(entity));
    }

    public static String table(String entity) {
        return Names.snake(Names.plural(Names.kebab(entity)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
