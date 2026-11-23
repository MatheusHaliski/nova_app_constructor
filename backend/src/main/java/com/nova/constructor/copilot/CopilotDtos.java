package com.nova.constructor.copilot;

import com.nova.constructor.generator.ProjectSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Contratos do Copilot do formulário de criação. */
public final class CopilotDtos {

    private CopilotDtos() {}

    public record ProposeRequest(
            @NotBlank(message = "Descreva a ideia do app.") @Size(max = 4000, message = "Use até 4000 caracteres.") String idea,
            String type) {}

    public record BacklogItem(String title, String story) {}

    /**
     * Proposta do Copilot total: a especificação pronta para revisar, mais requisitos e backlog.
     *
     * @param source "claude" quando veio da IA, "regras" quando veio das regras simples
     * @param notice aviso ao usuário (ex.: IA indisponível)
     */
    public record Proposal(ProjectSpec spec, List<String> requirements, List<BacklogItem> backlog, String rationale,
                           String source, String notice) {}

    /** Ajuda em um campo do formulário assistido. */
    public record AssistRequest(
            @NotBlank String field,
            @Size(max = 4000) String idea,
            @Size(max = 80) String name,
            @Size(max = 4000) String current) {}

    public record AssistResponse(String suggestion, String source, String notice) {}

    public record Status(String provider, String model) {}
}
