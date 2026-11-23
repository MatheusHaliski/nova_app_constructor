package com.nova.constructor.generator;

import java.util.List;

/**
 * Plano de geração: a especificação normalizada (com padrões preenchidos), os pacotes e plugins resolvidos com o
 * motivo de cada um, as variáveis de ambiente, os comandos de compilação e os arquivos que serão criados.
 */
public record GenerationPlan(ProjectSpec spec, String slug, Side backend, Side frontend, List<Env> env,
                             List<String> files, List<String> warnings) {

    /**
     * @param coordinate coordenada do pacote no gerenciador da stack
     * @param scope compile, runtime, test, provided ou dev
     * @param kind dependency ou plugin
     * @param reason por que entrou (obrigatório, escolhido, banco, conector, recurso, IA)
     */
    public record Artifact(String coordinate, String scope, String kind, String reason) {}

    public record Commands(String tool, String build, String run, String test) {}

    /** Um lado do projeto (backend ou frontend) já resolvido. */
    public record Side(String id, String name, String version, String language, String languageVersion,
                       List<Artifact> dependencies, List<Artifact> plugins, Commands commands) {}

    public record Env(String name, String example, boolean secret, String usedBy) {}
}
