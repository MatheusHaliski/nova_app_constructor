package com.nova.constructor.config;

import com.nova.constructor.catalog.Catalog;
import com.nova.constructor.copilot.ClaudeCopilot;
import com.nova.constructor.copilot.CopilotAi;
import com.nova.constructor.copilot.CopilotDraft;
import com.nova.constructor.insight.ClaudeImprovementAi;
import com.nova.constructor.insight.ImprovementAi;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * IA do NOVA. Com ANTHROPIC_API_KEY, Copilot e melhorias usam o Claude (modelo em NOVA_AI_MODEL); sem a chave, as
 * implementações ficam indisponíveis e cada função cai para as regras ou avisa que precisa da IA.
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    CopilotAi copilotAi(NovaProperties props) {
        String key = props.ai().anthropicApiKey();
        CopilotAi ai = key == null || key.isBlank() ? new NoCopilotAi() : new ClaudeCopilot(key, props.ai().model());
        log.info("IA do NOVA - Copilot: {} (modelo: {})", ai.getClass().getSimpleName(), ai.available() ? ai.model() : "nenhum, sem chave");
        return ai;
    }

    @Bean
    ImprovementAi improvementAi(NovaProperties props, JsonMapper mapper) {
        String key = props.ai().anthropicApiKey();
        ImprovementAi ai = key == null || key.isBlank() ? new NoImprovementAi() : new ClaudeImprovementAi(key, props.ai().model(), mapper);
        log.info("IA do NOVA - melhorias e perguntas: {} (modelo: {})", ai.getClass().getSimpleName(), ai.available() ? ai.model() : "nenhum, sem chave");
        return ai;
    }

    static final class NoCopilotAi implements CopilotAi {
        @Override public boolean available() { return false; }
        @Override public String model() { return null; }
        @Override public Optional<CopilotDraft> propose(String idea, String type, Catalog catalog) { return Optional.empty(); }
        @Override public Optional<String> assist(String field, String idea, String name, String current) { return Optional.empty(); }
        @Override public Optional<CopilotDraft.SketchDraft> sketch(byte[] image, String mediaType, String hint) { return Optional.empty(); }
    }

    static final class NoImprovementAi implements ImprovementAi {
        @Override public boolean available() { return false; }
        @Override public String model() { return null; }
        @Override public Optional<Result> apply(Request request) { return Optional.empty(); }
        @Override public Optional<Answer> ask(String question, List<String> tree, Map<String, String> files) { return Optional.empty(); }
    }
}
