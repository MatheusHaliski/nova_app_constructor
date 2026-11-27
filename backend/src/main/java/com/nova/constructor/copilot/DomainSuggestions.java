package com.nova.constructor.copilot;

import com.nova.constructor.generator.ProjectSpec.EntitySpec;
import com.nova.constructor.generator.ProjectSpec.FieldSpec;
import com.nova.constructor.generator.ProjectSpec.Requirement;
import com.nova.constructor.generator.ProjectSpec.ScreenSpec;
import com.nova.constructor.insight.Improvements;
import com.nova.constructor.insight.Names;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deduz os dados do domínio a partir dos nomes das telas: que entidades o app guarda, que campos cada uma tem, qual
 * modelo cada tela usa e os requisitos com critério de aceite. Usado pelas regras e quando a IA não devolve essa parte.
 */
final class DomainSuggestions {

    private static final Set<String> STOP = Set.of("meu", "meus", "minha", "minhas", "lista", "de", "da", "do", "das", "dos", "e", "o", "a", "os", "as",
            "tela", "pagina", "todos", "todas");
    private static final Set<String> BLANK = Set.of("carrinho", "cart", "busca", "search", "pesquisa", "sobre", "about", "contato", "contact", "ajuda",
            "help", "faq", "termo", "privacidade", "politica", "checkout", "pagamento", "mapa");

    /** Nomes de tela que falam do mesmo dado com outra palavra (ex.: Agenda e Novo agendamento → Agendamento). */
    private static final Map<String, String> SYNONYMS = Map.of("agenda", "agendamento", "catalogo", "produto", "vitrine", "produto", "estoque", "produto",
            "cardapio", "produto", "blog", "publicacao", "noticia", "publicacao", "artigo", "publicacao", "post", "publicacao");

    private DomainSuggestions() {}

    record Result(List<EntitySpec> entities, List<ScreenSpec> screens, List<Requirement> requirements) {}

    static Result fromScreens(List<String> screenNames, boolean auth) {
        Map<String, EntitySpec> entities = new LinkedHashMap<>();
        List<ScreenSpec> screens = new ArrayList<>();
        for (String name : screenNames) {
            List<String> words = new ArrayList<>();
            for (String w : Names.words(name)) {
                String n = Names.normalize(w);
                if (!n.isEmpty() && !STOP.contains(n)) {
                    words.add(n);
                }
            }
            if (words.isEmpty()) {
                continue;
            }
            String first = words.getFirst();
            String template;
            List<String> subject = words;
            if (first.matches("detalhe|detalhes|detail|ver")) {
                template = "DETAIL";
                subject = words.subList(1, words.size());
            } else if (first.matches("novo|nova|criar|adicionar|editar|cadastrar|registrar") && words.size() > 1) {
                template = "FORM";
                subject = words.subList(1, words.size());
            } else if (words.size() == 1 && first.matches("login|entrar|acesso")) {
                template = "LOGIN";
            } else if (words.size() == 1 && first.matches("cadastro|registro|signup|cadastrese|criarconta")) {
                template = "SIGNUP";
            } else if (first.matches("perfil|conta|configuracao|configuracoes|preferencias|settings")) {
                template = "SETTINGS";
            } else if (first.matches("painel|dashboard|resumo|indicadores")) {
                template = "DASHBOARD";
            } else if (first.matches("inicio|home") || BLANK.contains(Names.singular(first)) || BLANK.contains(first)) {
                template = first.matches("inicio|home") ? "LANDING" : "BLANK";
            } else {
                template = "LIST";
            }
            String entity = null;
            if (List.of("LIST", "DETAIL", "FORM").contains(template)) {
                if (subject.isEmpty()) {
                    template = "BLANK";
                } else {
                    String joined = String.join(" ", subject.stream().map(Names::singular).toList());
                    entity = Names.pascal(SYNONYMS.getOrDefault(Names.normalize(joined), joined));
                    String key = Names.key(String.join("", subject));
                    final String entityName = entity;
                    entities.computeIfAbsent(entity, k -> new EntitySpec(entityName, fields(key)));
                }
            }
            String access = auth && List.of("FORM", "SETTINGS", "DASHBOARD").contains(template) ? "AUTH" : "PUBLIC";
            screens.add(new ScreenSpec(name, template, entity, access, !List.of("DETAIL", "LOGIN", "SIGNUP").contains(template), null));
        }
        List<Requirement> requirements = new ArrayList<>();
        for (EntitySpec e : entities.values()) {
            String plural = Names.plural(Names.kebab(e.name())).replace('-', ' ');
            requirements.add(new Requirement("O sistema deve permitir listar, ver, criar, editar e excluir " + plural + ".",
                    "Uma pessoa autorizada cria um item de " + e.name() + ", ele aparece na lista e pode ser editado e excluído.", "ESSENTIAL"));
            for (FieldSpec f : e.fields()) {
                if ("STATUS".equals(f.type())) {
                    requirements.add(new Requirement("O sistema deve permitir acompanhar e mudar o " + f.name() + " de " + plural + ".",
                            "Um item de " + e.name() + " começa como " + f.values().getFirst() + " e só passa para os valores permitidos.", "IMPORTANT"));
                }
            }
        }
        if (auth) {
            requirements.add(new Requirement("O sistema deve exigir login para alterar dados.",
                    "Sem token, criar, editar ou excluir responde 401; com token, a operação é aceita.", "ESSENTIAL"));
        }
        return new Result(new ArrayList<>(entities.values()), screens, requirements);
    }

    @SuppressWarnings("unchecked")
    private static List<FieldSpec> fields(String key) {
        List<FieldSpec> out = new ArrayList<>();
        for (Map<String, Object> m : Improvements.defaultFields(key)) {
            out.add(new FieldSpec((String) m.get("name"), (String) m.get("type"), (Boolean) m.get("required"), (List<String>) m.get("values")));
        }
        return out;
    }
}
