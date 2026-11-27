package com.nova.constructor.insight;

import com.nova.constructor.generator.ProjectSpec;
import com.nova.constructor.insight.Analyzer.Context;
import com.nova.constructor.insight.Model.A11yFinding;
import com.nova.constructor.insight.Model.Category;
import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.Field;
import com.nova.constructor.insight.Model.Flow;
import com.nova.constructor.insight.Model.Operation;
import com.nova.constructor.insight.Model.PrivacyFinding;
import com.nova.constructor.insight.Model.Proposal;
import com.nova.constructor.insight.Model.RequirementStatus;
import com.nova.constructor.insight.Model.Resource;
import com.nova.constructor.insight.Model.Screen;
import com.nova.constructor.insight.Model.Severity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Janelas do visualizador além das camadas: requisitos ligados ao código, fluxos, dados pessoais (LGPD) e acessibilidade. */
final class Extras {

    private static final Set<String> STOP = Set.of("o", "a", "os", "as", "de", "da", "do", "das", "dos", "e", "ou", "um", "uma", "para", "com", "sem",
            "que", "deve", "devem", "sistema", "permitir", "poder", "pode", "usuario", "usuarios", "pessoa", "pessoas", "cliente", "clientes", "app",
            "seu", "sua", "seus", "suas", "no", "na", "nos", "nas", "ao", "aos", "por", "pelo", "pela", "ser", "ter", "todo", "toda", "cada");

    private Extras() {}

    // ---------------- requisitos ----------------

    static List<RequirementStatus> requirements(Context ctx) {
        ProjectSpec spec = ctx.spec();
        if (spec == null) {
            return List.of();
        }
        List<RequirementStatus> out = new ArrayList<>();
        for (ProjectSpec.Requirement r : spec.requirementsOrEmpty()) {
            String text = Names.normalize(" " + r.text() + " ").isEmpty() ? "" : Names.stripAccents(r.text()).toLowerCase(Locale.ROOT);
            Set<Operation> wanted = EnumSet.noneOf(Operation.class);
            if (text.matches("(?s).*\\b(listar|lista|ver|visualizar|consultar|mostrar|exibir|buscar|acompanhar)\\b.*")) {
                wanted.add(Operation.LIST);
            }
            if (text.matches("(?s).*\\b(ver|detalhe|detalhes|visualizar|consultar)\\b.*")) {
                wanted.add(Operation.READ);
            }
            if (text.matches("(?s).*\\b(criar|cadastrar|registrar|adicionar|incluir|novo|nova)\\b.*")) {
                wanted.add(Operation.CREATE);
            }
            if (text.matches("(?s).*\\b(editar|alterar|atualizar|corrigir)\\b.*")) {
                wanted.add(Operation.UPDATE);
            }
            if (text.matches("(?s).*\\b(excluir|remover|apagar|deletar)\\b.*")) {
                wanted.add(Operation.DELETE);
            }
            boolean status = text.matches("(?s).*\\b(status|situacao|andamento|etapa)\\b.*");
            boolean login = text.matches("(?s).*\\b(login|autenticar|autenticacao|token|senha|entrar)\\b.*");
            List<String> evidence = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            Entity entity = matchEntity(ctx, text);
            if (entity != null) {
                Resource res = ctx.resources().stream().filter(x -> entity.name().equals(x.entity())).findFirst().orElse(null);
                for (Operation op : wanted) {
                    Endpoint e = res == null ? null : res.operations().get(op);
                    if (e != null) {
                        evidence.add(e.method() + " " + e.path());
                    } else {
                        missing.add(SpringCode.describe(Set.of(op)) + " " + entity.name());
                    }
                }
                if (status) {
                    Endpoint e = ctx.facts().endpoints.stream().filter(x -> !x.method().equals("GET") && x.path().contains("status")
                            && (res == null || x.path().startsWith(res.path()))).findFirst().orElse(null);
                    if (e != null) {
                        evidence.add(e.method() + " " + e.path());
                    } else {
                        missing.add("rota para mudar o status de " + entity.name());
                    }
                }
                for (Screen s : ctx.screens()) {
                    if (res != null && s.apiCalls().stream().anyMatch(c -> c.startsWith(res.path()))) {
                        evidence.add("tela " + s.route());
                    }
                }
                if (ctx.facts().tested(entity.name() + "Controller") || ctx.facts().tested(entity.name())) {
                    evidence.add("testes de " + entity.name());
                } else {
                    missing.add("testes de " + entity.name());
                }
            }
            if (login) {
                if (ctx.stack().auth()) {
                    evidence.add("autenticação por token");
                } else {
                    missing.add("autenticação");
                }
            }
            String state;
            if (entity == null && !login) {
                state = "UNKNOWN";
            } else if (missing.isEmpty()) {
                state = "COVERED";
            } else {
                state = evidence.stream().anyMatch(x -> !x.startsWith("tela ")) ? "PARTIAL" : "MISSING";
            }
            out.add(new RequirementStatus(r.text(), r.acceptance(), r.priority(), state, evidence, missing));
        }
        return out;
    }

    private static Entity matchEntity(Context ctx, String text) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : Names.words(text)) {
            String n = Names.normalize(w);
            if (!n.isEmpty() && !STOP.contains(n)) {
                words.add(Names.singular(n));
            }
        }
        for (Entity e : ctx.facts().entities) {
            String key = Names.key(e.name());
            String table = Names.key(e.table());
            if (words.contains(key) || words.contains(table) || words.stream().anyMatch(w -> w.length() > 3 && key.startsWith(w))) {
                return e;
            }
        }
        return null;
    }

    // ---------------- fluxos ----------------

    static List<Flow> flows(Context ctx) {
        Map<String, Facts.Controller> byFile = new LinkedHashMap<>();
        ctx.facts().controllers.forEach(c -> byFile.put(c.file(), c));
        List<Flow> flows = new ArrayList<>();
        for (Endpoint e : ctx.facts().endpoints) {
            Resource res = ctx.resources().stream().filter(r -> r.operations().containsValue(e) || r.actions().contains(e)).findFirst().orElse(null);
            Entity entity = res == null || res.entity() == null ? null
                    : ctx.facts().entities.stream().filter(x -> x.name().equals(res.entity())).findFirst().orElse(null);
            List<String> screens = ctx.screens().stream().filter(s -> s.apiCalls().stream().anyMatch(c -> samePath(c, e.path()))).map(Screen::route).toList();
            Facts.Controller c = byFile.get(e.file());
            List<String> services = new ArrayList<>();
            String repository = null;
            if (c != null && c.constructor() != null) {
                for (Facts.CtorParam p : c.constructor()) {
                    if (p.type().endsWith("Repository")) {
                        repository = p.type();
                    } else if (p.type().endsWith("Service")) {
                        services.add(p.type());
                        String serviceFile = ctx.src().paths("backend/src/main/java/.*/" + p.type() + "\\.java").stream().findFirst().orElse(null);
                        if (serviceFile != null && repository == null) {
                            repository = Sources.first(Pattern.compile("private\\s+final\\s+(\\w+Repository)\\s"), ctx.src().get(serviceFile), 1);
                        }
                    }
                }
            }
            if (repository == null && entity != null) {
                repository = entity.repository();
            }
            flows.add(new Flow(e.method(), e.path(), screens, c == null ? Sources.fileName(e.file()) : c.name(), services, repository,
                    entity == null ? null : entity.name(), entity == null ? null : entity.table(), e.access()));
        }
        return flows;
    }

    private static boolean samePath(String call, String path) {
        return call.replaceAll("\\{[^}]*\\}", "*").equals(path.replaceAll("\\{[^}]*\\}", "*"));
    }

    // ---------------- dados pessoais (LGPD) ----------------

    private record Kind(String kind, Pattern names, List<String> articles, String advice) {}

    private static final List<Kind> KINDS = List.of(
            new Kind("SENSITIVE", Pattern.compile("saude|diagnostico|doenca|alergia|religia|etnia|raca|biometri|genetic|sexual|orientacao|sindica|politic|deficiencia|prontuario|medicament"),
                    List.of("Art. 5º, II", "Art. 11", "Art. 46"),
                    "Dado pessoal sensível: só trate com consentimento específico ou outra base do art. 11, guarde com acesso restrito e nunca exponha em rota pública."),
            new Kind("IDENTIFICATION", Pattern.compile("^(cpf|rg|cnh|passaporte|documento|cnpj)|(cpf|rg|documento)$"), List.of("Art. 6º, III", "Art. 46"),
                    "Documento de identificação: mostre mascarado (ex.: ***.123.456-**), não use como chave em URLs e limite quem consulta."),
            new Kind("CONTACT", Pattern.compile("email|telefone|celular|whatsapp|phone"), List.of("Art. 6º, I e III", "Art. 18"),
                    "Contato pessoal: use só para a finalidade informada e permita que a pessoa corrija ou peça a exclusão."),
            new Kind("LOCATION", Pattern.compile("endereco|logradouro|^cep$|cep$|bairro|latitude|longitude|geoloc|address"), List.of("Art. 6º, III", "Art. 46"),
                    "Localização: guarde só o necessário (ex.: cidade em vez do endereço completo, se bastar) e proteja contra acesso público."),
            new Kind("BIRTH", Pattern.compile("nascimento|birth|idade"), List.of("Art. 6º, III", "Art. 14"),
                    "Data de nascimento: se puder haver crianças ou adolescentes, o tratamento exige cuidados do art. 14."));

    static List<PrivacyFinding> privacy(Context ctx) {
        List<PrivacyFinding> out = new ArrayList<>();
        for (Entity e : ctx.facts().entities) {
            Resource res = ctx.resources().stream().filter(r -> e.name().equals(r.entity())).findFirst().orElse(null);
            boolean publicRead = res != null && res.operations().entrySet().stream()
                    .anyMatch(x -> (x.getKey() == Operation.LIST || x.getKey() == Operation.READ) && "PUBLIC".equals(x.getValue().access()));
            for (Field f : e.fields()) {
                String n = Names.normalize(f.name());
                for (Kind k : KINDS) {
                    if (k.names().matcher(n).find()) {
                        List<String> articles = new ArrayList<>(k.articles());
                        String advice = k.advice();
                        if (publicRead) {
                            advice += " Hoje a rota de leitura de " + e.name() + " é pública.";
                        }
                        out.add(new PrivacyFinding(e.name(), f.name(), k.kind(), publicRead, articles, advice));
                        break;
                    }
                }
            }
        }
        return out;
    }

    static List<Proposal> privacyProposals(Context ctx, List<PrivacyFinding> findings) {
        Map<String, List<PrivacyFinding>> exposed = new LinkedHashMap<>();
        for (PrivacyFinding f : findings) {
            if (f.publicRead()) {
                exposed.computeIfAbsent(f.entity(), k -> new ArrayList<>()).add(f);
            }
        }
        List<Proposal> list = new ArrayList<>();
        for (Map.Entry<String, List<PrivacyFinding>> e : exposed.entrySet()) {
            Entity entity = ctx.facts().entities.stream().filter(x -> x.name().equals(e.getKey())).findFirst().orElseThrow();
            boolean sensitive = e.getValue().stream().anyMatch(f -> f.kind().equals("SENSITIVE"));
            list.add(new Proposal(Improvements.id("privacy", e.getKey()), "privacy", Category.PRIVACY, Severity.GAP,
                    "PRIVACIDADE · " + e.getKey().toUpperCase(Locale.ROOT), "Proteger os dados pessoais de " + e.getKey(),
                    "A leitura de " + e.getKey() + " é pública e expõe " + String.join(", ", e.getValue().stream().map(PrivacyFinding::field).toList())
                            + (sensitive ? ", incluindo dado sensível (LGPD, art. 11)." : " (LGPD, art. 6º, III, e art. 46)."),
                    List.of(entity.file()), List.of(), false,
                    "Exija login para ler " + e.getKey() + " ou crie uma rota pública que devolva só os campos sem dados pessoais. "
                            + "Registre a finalidade de cada dado e por quanto tempo ele é guardado."));
        }
        return list;
    }

    // ---------------- acessibilidade ----------------

    private static final Pattern IMG = Pattern.compile("<img\\b(?![^>]*\\balt=)[^>]*>");
    private static final Pattern EMPTY_BUTTON = Pattern.compile("<button\\b(?![^>]*aria-label)[^>]*>\\s*(?:<svg[\\s\\S]*?</svg>\\s*)?</button>|<button\\b(?![^>]*aria-label)[^>]*/>");
    private static final Pattern INPUT = Pattern.compile("<(input|textarea|select)\\b(?![^>]*(aria-label|aria-labelledby|type=\"hidden\"|type=\"submit\"|type=\"button\"))[^>]*>");
    private static final Pattern CLICK_DIV = Pattern.compile("<(div|span)\\b[^>]*\\bonClick=");

    static List<A11yFinding> accessibility(Context ctx) {
        List<A11yFinding> out = new ArrayList<>();
        Set<String> pages = new LinkedHashSet<>(ctx.screens().stream().map(Screen::file).toList());
        for (String path : ctx.src().paths("frontend/src/.+\\.(tsx|jsx)")) {
            String text = ctx.src().get(path);
            add(out, path, text, IMG, "img-alt", "Imagem sem texto alternativo (alt). Leitores de tela não sabem o que ela mostra.");
            add(out, path, text, EMPTY_BUTTON, "button-name", "Botão sem texto nem aria-label: quem usa leitor de tela não sabe o que ele faz.");
            add(out, path, text, CLICK_DIV, "click-div", "Clique em div ou span: o teclado não alcança. Use <button> ou <a>.");
            Matcher m = INPUT.matcher(text);
            while (m.find()) {
                int open = text.lastIndexOf("<label", m.start());
                int close = text.lastIndexOf("</label>", m.start());
                boolean wrapped = open > close;
                String id = Sources.first(Pattern.compile("\\bid=\"([^\"]+)\""), m.group(), 1);
                boolean labelled = id != null && text.contains("htmlFor=\"" + id + "\"");
                if (!wrapped && !labelled) {
                    out.add(new A11yFinding(path, Sources.line(text, m.start()), "input-label", "Campo sem rótulo associado (<label> ou aria-label)."));
                }
            }
            if (pages.contains(path) && !text.contains("<h1")) {
                out.add(new A11yFinding(path, 1, "page-title", "Página sem título principal (<h1>)."));
            }
            if (path.matches("frontend/src/app/layout\\.(tsx|jsx)") && !text.matches("(?s).*<html[^>]*\\blang=.*")) {
                out.add(new A11yFinding(path, 1, "html-lang", "O <html> não declara o idioma (lang). Leitores de tela podem ler com a pronúncia errada."));
            }
        }
        return out;
    }

    private static void add(List<A11yFinding> out, String path, String text, Pattern p, String rule, String message) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(new A11yFinding(path, Sources.line(text, m.start()), rule, message));
        }
    }

    static List<Proposal> a11yProposals(List<A11yFinding> findings) {
        Map<String, List<A11yFinding>> byFile = new LinkedHashMap<>();
        findings.forEach(f -> byFile.computeIfAbsent(f.file(), k -> new ArrayList<>()).add(f));
        List<Proposal> list = new ArrayList<>();
        for (Map.Entry<String, List<A11yFinding>> e : byFile.entrySet()) {
            list.add(new Proposal(Improvements.id("a11y", e.getKey()), "a11y", Category.SCREENS, Severity.SUGGESTION,
                    "ACESSIBILIDADE · " + Sources.fileName(e.getKey()).toUpperCase(Locale.ROOT), "Corrigir acessibilidade em " + Sources.fileName(e.getKey()),
                    String.join(" ", e.getValue().stream().map(f -> "Linha " + f.line() + ": " + f.message()).distinct().limit(4).toList()),
                    List.of(e.getKey()), List.of(), false, "Siga cada item: texto alternativo em imagens, rótulo em campos, botões com nome e títulos de página."));
        }
        return list;
    }
}
