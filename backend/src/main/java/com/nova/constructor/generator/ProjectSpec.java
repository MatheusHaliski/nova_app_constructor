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
        @Size(max = 30, message = "Use até 30 telas.") List<@Size(max = 60) String> screens) {

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
