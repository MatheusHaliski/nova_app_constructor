package com.nova.constructor.copilot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Copilot sem IA: deduz uma proposta inicial por palavras-chave. É usado quando não há chave da API configurada
 * ou quando a IA falha, para o formulário nunca ficar sem sugestão.
 */
@Component
public class RuleBasedCopilot {

    public CopilotDraft propose(String idea, String type) {
        String text = Normalizer.normalize(idea, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        boolean auth = has(text, "login", "usuario", "cadastro", "conta", "perfil", "senha", "cliente");
        boolean shop = has(text, "loja", "produto", "pedido", "venda", "carrinho", "e-commerce", "ecommerce");
        boolean schedule = has(text, "agenda", "agendamento", "horario", "consulta", "reserva");
        boolean content = has(text, "blog", "noticia", "artigo", "post", "conteudo");

        List<String> connectors = new ArrayList<>();
        if (has(text, "busca", "pesquisa", "procurar")) connectors.add("opensearch");
        if (has(text, "upload", "imagem", "foto", "arquivo", "video", "midia")) connectors.add("s3");
        if (has(text, "cache", "ranking", "tempo real", "contador")) connectors.add("redis");
        if (has(text, "notificac", "timeline", "feed", "historico de eventos")) connectors.add("cassandra");
        boolean ai = has(text, " ia ", "inteligencia artificial", "chatbot", "assistente", "recomendac", "resumo automatico");

        Set<String> screens = new LinkedHashSet<>(List.of("Início"));
        if (auth) screens.addAll(List.of("Login", "Cadastro"));
        if (shop) screens.addAll(List.of("Produtos", "Detalhe do produto", "Carrinho", "Pedidos"));
        if (schedule) screens.addAll(List.of("Agenda", "Novo agendamento"));
        if (content) screens.addAll(List.of("Publicações", "Publicação"));
        if (connectors.contains("opensearch")) screens.add("Busca");
        if (auth) screens.add("Perfil");

        List<String> features = new ArrayList<>(List.of("openapi", "docker", "tests"));
        if (auth) features.add(0, "auth");

        List<String> requirements = new ArrayList<>();
        List<CopilotDraft.Story> backlog = new ArrayList<>();
        for (String screen : screens) {
            requirements.add("O sistema deve oferecer a tela \"" + screen + "\".");
            backlog.add(new CopilotDraft.Story(screen, "Como usuário, quero acessar a tela " + screen + ", para usar essa parte do app."));
        }
        if (auth) {
            requirements.add("O sistema deve autenticar usuários com token e proteger as rotas privadas.");
        }

        boolean mobile = "APP".equals(type) || has(text, "aplicativo", "mobile", "celular");
        return new CopilotDraft(name(idea), firstSentence(idea), idea.trim(), type != null ? type : mobile ? "APP" : "WEB_APP",
                "spring-boot", "nextjs", "mysql", connectors, ai, features, List.copyOf(screens), requirements, backlog,
                "Proposta inicial por regras simples: Spring Boot, Next.js e MySQL (a stack padrão do NOVA), com recursos e "
                        + "conectores deduzidos das palavras da sua descrição. Revise cada escolha.", null, null);
    }

    public String assist(String field, String idea, String name) {
        String subject = name == null || name.isBlank() ? "o app" : name;
        String base = idea == null || idea.isBlank() ? "" : idea.trim();
        return switch (field) {
            case "description" -> base.isEmpty() ? "Descreva em uma frase o que " + subject + " faz e para quem." : firstSentence(base);
            case "objective" -> base.isEmpty()
                    ? "Explique o problema que " + subject + " resolve, quem vai usar e qual resultado espera."
                    : "O objetivo de " + subject + " é: " + base + " O público principal são as pessoas que hoje fazem isso sem uma ferramenta dedicada.";
            case "screens" -> String.join("\n", propose(base.isEmpty() ? subject : base, null).screens());
            case "requirements" -> String.join("\n", propose(base.isEmpty() ? subject : base, null).requirements());
            default -> "Descreva este campo com suas palavras; a sugestão automática está disponível para descrição, objetivo, telas e requisitos.";
        };
    }

    private static boolean has(String text, String... words) {
        String padded = " " + text + " ";
        for (String w : words) {
            if (padded.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of("um", "uma", "o", "a", "os", "as", "de", "da", "do", "das", "dos",
            "para", "com", "e", "em", "que", "quero", "criar", "fazer", "app", "aplicativo", "sistema", "site");

    /** Nome curto: as três primeiras palavras com significado (sem artigos e preposições). */
    private static String name(String idea) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String raw : idea.trim().split("\\s+")) {
            String w = raw.replaceAll("[^\\p{L}\\p{N}]", "");
            if (w.isEmpty() || STOPWORDS.contains(w.toLowerCase(Locale.ROOT))) {
                continue;
            }
            sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            if (++count == 3) {
                break;
            }
        }
        return sb.isEmpty() ? "Meu App" : sb.toString();
    }

    private static String firstSentence(String idea) {
        String s = idea.trim().split("(?<=[.!?])\\s")[0];
        return s.length() > 200 ? s.substring(0, 197) + "..." : s;
    }
}
