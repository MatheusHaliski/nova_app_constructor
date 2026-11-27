package com.nova.constructor.copilot;

import com.nova.constructor.catalog.Catalog;
import java.util.Optional;

/** IA do Copilot. Sem chave configurada, a implementação fica indisponível e o Copilot usa as regras. */
public interface CopilotAi {

    default boolean available() {
        return true;
    }

    String model();

    Optional<CopilotDraft> propose(String idea, String type, Catalog catalog);

    Optional<String> assist(String field, String idea, String name, String current);

    Optional<CopilotDraft.SketchDraft> sketch(byte[] image, String mediaType, String hint);
}
