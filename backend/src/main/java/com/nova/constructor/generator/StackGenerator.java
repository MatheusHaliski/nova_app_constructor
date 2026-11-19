package com.nova.constructor.generator;

import java.util.Map;

/** Gera os arquivos iniciais de uma stack (backend ou frontend) dentro de uma pasta do projeto. */
public interface StackGenerator {

    /** Id da stack no catálogo (ex.: spring-boot, nextjs). */
    String id();

    /**
     * @param plan plano já validado e resolvido
     * @param root pasta de destino com barra final (ex.: {@code backend/})
     * @param files mapa caminho → conteúdo que recebe os arquivos gerados
     */
    void generate(GenerationPlan plan, String root, Map<String, String> files);
}
