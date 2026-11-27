package com.nova.constructor.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Especificação de um projeto: exatamente o que o formulário de criação envia (modo Copilot total ou assistido).
 * Campos de escolha usam os ids do catálogo; listas nulas significam "usar o padrão do catálogo".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectSpec(
        @NotBlank(message = "Dê um nome ao projeto.") @Size(max = 80, message = "Use até 80 caracteres.") String name,
        @Size(max = 500, message = "Use até 500 caracteres.") String description,
        @Size(max = 4000, message = "Use até 4000 caracteres.") String objective,
        @NotBlank(message = "Escolha o tipo do projeto.") String type,
        String mode,
        @NotNull(message = "Escolha o backend.") @Valid Backend backend,
        @Valid Frontend frontend,
        @Valid Database database,
        List<String> connectors,
        @Valid Ai ai,
        List<String> features,
        @Size(max = 30, message = "Use até 30 telas.") List<@Size(max = 60) String> screens,
        @Size(max = 15, message = "Use até 15 entidades.") List<@Valid EntitySpec> entities,
        @Size(max = 30, message = "Use até 30 telas.") List<@Valid ScreenSpec> screenSpecs,
        @Size(max = 50, message = "Use até 50 requisitos.") List<@Valid Requirement> requirements,
        @Valid Theme theme) {

    /** Dados que o app guarda: viram entidade, repositório, API, migração e testes no backend. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntitySpec(@NotBlank(message = "Dê um nome à entidade.") @Size(max = 40) String name,
                             @Size(max = 30, message = "Use até 30 campos por entidade.") List<@Valid FieldSpec> fields) {}

    /**
     * Campo de uma entidade.
     *
     * @param type TEXT, LONG_TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME ou STATUS
     * @param values valores possíveis, só para STATUS (o primeiro é o inicial)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldSpec(@NotBlank @Size(max = 40) String name, String type, Boolean required, @Size(max = 12) List<@Size(max = 30) String> values) {}

    /**
     * Caracterização de uma tela. O nome também aparece em {@code screens}.
     *
     * @param template LIST, DETAIL, FORM, DASHBOARD, LOGIN, SIGNUP, LANDING, SETTINGS ou BLANK
     * @param entity   entidade mostrada (LIST, DETAIL e FORM)
     * @param access   PUBLIC ou AUTH
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScreenSpec(@NotBlank @Size(max = 60) String name, String template, @Size(max = 40) String entity, String access,
                             Boolean inMenu, @Size(max = 300) String purpose) {}

    /** @param priority ESSENTIAL, IMPORTANT ou LATER */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Requirement(@NotBlank @Size(max = 300) String text, @Size(max = 500) String acceptance, String priority) {}

    /** Tema do frontend por tokens: vale para todas as telas. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Theme(String primary, String font, String radius, String density) {}

    public List<EntitySpec> entitiesOrEmpty() {
        return entities == null ? List.of() : entities;
    }

    public List<ScreenSpec> screenSpecsOrEmpty() {
        return screenSpecs == null ? List.of() : screenSpecs;
    }

    public List<Requirement> requirementsOrEmpty() {
        return requirements == null ? List.of() : requirements;
    }

    /** Mesma especificação com outro backend (usado para comparar stacks). */
    public ProjectSpec withBackend(Backend newBackend) {
        return new ProjectSpec(name, description, objective, type, mode, newBackend, frontend, database, connectors, ai, features, screens,
                entities, screenSpecs, requirements, theme);
    }

    /** Mesma especificação com outros recursos (usado pelas melhorias que geram CI e Docker). */
    public ProjectSpec withFeatures(List<String> newFeatures) {
        return new ProjectSpec(name, description, objective, type, mode, backend, frontend, database, connectors, ai, newFeatures, screens,
                entities, screenSpecs, requirements, theme);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Backend(@NotBlank(message = "Escolha o backend.") String id, String version, String language,
                          String languageVersion, String buildTool, List<String> packages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Frontend(@NotBlank String id, String version, List<String> packages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Database(@NotBlank String id, String version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ai(@NotBlank String provider, String model) {}

    public List<String> connectorsOrEmpty() {
        return connectors == null ? List.of() : connectors;
    }

    public List<String> screensOrEmpty() {
        return screens == null ? List.of() : screens;
    }

    public boolean hasFeature(String id) {
        return features != null && features.contains(id);
    }
}
