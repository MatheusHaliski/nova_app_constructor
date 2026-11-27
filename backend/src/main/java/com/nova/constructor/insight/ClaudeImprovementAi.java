package com.nova.constructor.insight;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/** Aplicação de melhorias com o Claude, por saída estruturada. */
public class ClaudeImprovementAi implements ImprovementAi {

    private static final String SYSTEM = """
            Você aplica melhorias em projetos de software gerados pelo NOVA, uma plataforma brasileira. A pessoa revisou uma \
            proposta de melhoria e as opções dela; seu trabalho é escrever a alteração no código do projeto.

            Regras:
            - Altere só o necessário para a melhoria. Mantenha o estilo, os nomes e a organização que o projeto já usa.
            - Devolva cada arquivo novo ou alterado com o conteúdo COMPLETO, não um trecho nem um diff.
            - Use só caminhos que existem na lista de arquivos, ou caminhos novos coerentes com ela. Nunca escreva em .env ou .git.
            - Nunca coloque senhas, tokens ou chaves no código: leia de variáveis de ambiente e documente no .env.example sem valor.
            - Se houver um rascunho do NOVA, parta dele e ajuste conforme a instrução da pessoa.
            - Escreva comentários e mensagens para o usuário final em português do Brasil.
            - No máximo 15 arquivos.""";

    private final AnthropicClient client;
    private final String model;
    private final JsonMapper mapper;

    public ClaudeImprovementAi(String apiKey, String model, JsonMapper mapper) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model;
        this.mapper = mapper;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public Optional<Answer> ask(String question, java.util.List<String> tree, Map<String, String> files) {
        StringBuilder user = new StringBuilder("# Pergunta\n").append(question).append("\n\n# Arquivos do projeto\n").append(String.join("\n", tree)).append('\n');
        for (Map.Entry<String, String> f : files.entrySet()) {
            user.append("\n## ").append(f.getKey()).append("\n```\n").append(numbered(f.getValue())).append("\n```\n");
        }
        StructuredMessageCreateParams<Answer> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system("""
                        Você responde perguntas sobre um projeto de software gerado pelo NOVA, em português do Brasil, para quem pode \
                        estar aprendendo. Use só o código enviado; se ele não basta, diga o que falta. Aponte arquivo e linha de cada coisa \
                        que citar. Não invente arquivos.""")
                .outputConfig(Answer.class)
                .addUserMessage(user.toString())
                .build();
        StructuredMessage<Answer> message = client.messages().create(params);
        if (message.stopReason().filter(reason -> reason.equals(StopReason.REFUSAL) || reason.equals(StopReason.MAX_TOKENS)).isPresent()) {
            return Optional.empty();
        }
        return message.content().stream().flatMap(block -> block.text().stream()).map(text -> text.text()).findFirst();
    }

    private static String numbered(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(i + 1).append(": ").append(lines[i]).append('\n');
        }
        return sb.toString();
    }

    @Override
    public Optional<Result> apply(Request r) {
        StringBuilder user = new StringBuilder("# Melhoria\n").append(r.title()).append("\n\n## Por quê\n").append(r.why()).append('\n');
        if (r.howTo() != null) {
            user.append("\n## Como fazer\n").append(r.howTo()).append('\n');
        }
        user.append("\n## Opções escolhidas pela pessoa\n").append(mapper.writeValueAsString(r.params())).append('\n');
        if (r.note() != null && !r.note().isBlank()) {
            user.append("\n## Instrução extra da pessoa\n").append(r.note()).append('\n');
        }
        user.append("\n## Arquivos do projeto\n").append(String.join("\n", r.tree())).append('\n');
        for (Map.Entry<String, String> f : r.files().entrySet()) {
            user.append("\n## Conteúdo de ").append(f.getKey()).append("\n```\n").append(f.getValue()).append("\n```\n");
        }
        for (Map.Entry<String, String> f : r.draft().entrySet()) {
            user.append("\n## Rascunho do NOVA para ").append(f.getKey()).append("\n```\n").append(f.getValue()).append("\n```\n");
        }
        StructuredMessageCreateParams<Result> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(32000L)
                .system(SYSTEM)
                .outputConfig(Result.class)
                .addUserMessage(user.toString())
                .build();
        StructuredMessage<Result> message = client.messages().create(params);
        if (message.stopReason().filter(reason -> reason.equals(StopReason.REFUSAL) || reason.equals(StopReason.MAX_TOKENS)).isPresent()) {
            return Optional.empty();
        }
        return message.content().stream().flatMap(block -> block.text().stream()).map(text -> text.text()).findFirst();
    }
}
