package com.nova.constructor.copilot;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.nova.constructor.catalog.Catalog;
import java.util.Optional;
import java.util.stream.Collectors;

/** Copilot com o Claude: saída estruturada para a proposta e texto livre para ajudar em um campo. */
public class ClaudeCopilot implements CopilotAi {

    private final AnthropicClient client;
    private final String model;

    public ClaudeCopilot(String apiKey, String model) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
    }

    @Override
    public String model() {
        return model;
    }

    /** Proposta completa; vazio quando o modelo recusa ou não devolve o formato esperado. */
    @Override
    public Optional<CopilotDraft> propose(String idea, String type, Catalog catalog) {
        StructuredMessageCreateParams<CopilotDraft> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(systemPrompt(catalog))
                .outputConfig(CopilotDraft.class)
                .addUserMessage((type == null ? "" : "Tipo já escolhido pelo usuário: " + type + "\n\n") + "Ideia do app:\n" + idea)
                .build();
        StructuredMessage<CopilotDraft> message = client.messages().create(params);
        if (message.stopReason().filter(reason -> reason.equals(StopReason.REFUSAL) || reason.equals(StopReason.MAX_TOKENS)).isPresent()) {
            return Optional.empty();
        }
        return message.content().stream().flatMap(block -> block.text().stream()).map(text -> text.text()).findFirst();
    }

    /** Lê um esboço de tela (imagem) e devolve telas, dados e elementos. */
    @Override
    public Optional<CopilotDraft.SketchDraft> sketch(byte[] image, String mediaType, String hint) {
        var source = com.anthropic.models.messages.Base64ImageSource.builder()
                .data(java.util.Base64.getEncoder().encodeToString(image))
                .mediaType(com.anthropic.models.messages.Base64ImageSource.MediaType.of(mediaType))
                .build();
        StructuredMessageCreateParams<CopilotDraft.SketchDraft> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system("""
                        Você é o Copilot do NOVA. A pessoa enviou a foto de um desenho de tela ou o print de outro app. Descreva as telas \
                        que vê como telas de um app novo: nome de cada tela, o modelo (LIST, DETAIL, FORM, DASHBOARD, LOGIN, SIGNUP, LANDING, \
                        SETTINGS ou BLANK), os dados que ela mostra (entidades e campos) e os elementos. Não copie marca, logotipo nem textos \
                        de outra empresa: descreva a estrutura. Responda em português do Brasil.""")
                .outputConfig(CopilotDraft.SketchDraft.class)
                .addUserMessageOfContentBlockParams(java.util.List.of(
                        com.anthropic.models.messages.ContentBlockParam.ofImage(source),
                        com.anthropic.models.messages.ContentBlockParam.ofText(hint == null || hint.isBlank()
                                ? "Descreva as telas deste esboço." : "O que manter: " + hint)))
                .build();
        StructuredMessage<CopilotDraft.SketchDraft> message = client.messages().create(params);
        if (message.stopReason().filter(reason -> reason.equals(StopReason.REFUSAL) || reason.equals(StopReason.MAX_TOKENS)).isPresent()) {
            return Optional.empty();
        }
        return message.content().stream().flatMap(block -> block.text().stream()).map(text -> text.text()).findFirst();
    }

    /** Sugestão de texto para um campo do formulário assistido. */
    @Override
    public Optional<String> assist(String field, String idea, String name, String current) {
        String instruction = switch (field) {
            case "description" -> "Escreva uma descrição de uma frase (até 200 caracteres) para o app.";
            case "objective" -> "Escreva o objetivo do app em um parágrafo: para quem é, que problema resolve e qual resultado entrega.";
            case "screens" -> "Liste as telas do app, uma por linha, só o nome de cada tela, na ordem de navegação, no máximo 12.";
            case "requirements" -> "Liste os requisitos funcionais do app, um por linha, começando por \"O sistema deve\".";
            default -> "Ajude a preencher o campo \"" + field + "\" do formulário de criação do app.";
        };
        Message message = client.messages().create(MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system("""
                        Você ajuda uma pessoa a preencher o formulário de criação de um app no NOVA, uma plataforma brasileira \
                        que gera projetos de software. Responda em português do Brasil, só com o texto do campo pedido, \
                        sem títulos, sem explicações e sem Markdown, porque a resposta é colada direto no campo.""")
                .addUserMessage(instruction
                        + "\n\nNome do app: " + (name == null || name.isBlank() ? "(ainda sem nome)" : name)
                        + "\nIdeia: " + (idea == null || idea.isBlank() ? "(não informada)" : idea)
                        + (current == null || current.isBlank() ? "" : "\nTexto atual do campo, para melhorar:\n" + current))
                .build());
        if (message.stopReason().filter(reason -> reason.equals(StopReason.REFUSAL)).isPresent()) {
            return Optional.empty();
        }
        String text = message.content().stream().flatMap(block -> block.text().stream()).map(t -> t.text()).collect(Collectors.joining()).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String systemPrompt(Catalog catalog) {
        return """
                Você é o Copilot do NOVA, uma plataforma brasileira que gera a estrutura inicial de projetos de software. \
                A pessoa descreve uma ideia de app e você propõe o projeto inteiro: nome, descrição, objetivo, tecnologias, \
                recursos, telas, requisitos e backlog. A proposta aparece num formulário que ela revisa e ajusta antes de \
                gerar o código, então prefira escolhas simples e justificáveis a escolhas sofisticadas.

                Escolha as tecnologias só entre os ids abaixo, que são os que o gerador do NOVA suporta hoje.
                Backends: %s
                Frontends: %s
                Bancos: %s
                Conectores opcionais: %s
                Recursos: %s

                O banco relacional é a fonte central de dados. Um conector opcional só entra quando a ideia pede \
                claramente o que ele faz, porque cada conector é mais um serviço para a pessoa manter. Marque IA externa \
                apenas se o app em si usar IA para os usuários dele. Ative o recurso "auth" quando houver contas, dados \
                por usuário ou áreas restritas.

                Escreva tudo em português do Brasil, em linguagem simples.""".formatted(
                catalog.backends().stream().filter(Catalog.Stack::available).map(s -> s.id() + " (" + s.description() + ")").collect(Collectors.joining("; ")),
                catalog.frontends().stream().filter(Catalog.Stack::available).map(s -> s.id() + " (" + s.description() + ")").collect(Collectors.joining("; ")),
                catalog.databases().stream().filter(Catalog.Database::available).map(d -> d.id() + " (" + d.description() + ")").collect(Collectors.joining("; ")),
                catalog.connectors().stream().map(c -> c.id() + " (" + c.description() + ")").collect(Collectors.joining("; ")),
                catalog.features().stream().map(f -> f.id() + " (" + f.name() + ")").collect(Collectors.joining("; ")));
    }
}
