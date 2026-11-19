package com.nova.constructor.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de tecnologias do NOVA (acervo): linguagens e versões, frameworks e versões, ferramentas de build, pacotes,
 * plugins, bancos, conectores e provedores de IA. É a fonte única do formulário, do Copilot e do gerador.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Catalog(String version, List<ProjectType> projectTypes, List<Language> languages, List<Stack> backends,
                      List<Stack> frontends, List<Database> databases, List<Connector> connectors,
                      List<AiProvider> aiProviders, List<Feature> features) {

    public static final String AVAILABLE = "available";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectType(String id, String name, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(String id, String name, String icon, List<String> versions, String defaultVersion, String description) {}

    /** Framework de backend ou de frontend, com versões, ferramentas de build e pacotes disponíveis. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stack(String id, String name, String icon, String status, String description, List<String> languages,
                        List<String> versions, String defaultVersion, List<BuildTool> buildTools, String defaultBuildTool,
                        List<Pkg> packages) {

        public boolean available() {
            return AVAILABLE.equals(status);
        }

        public Optional<BuildTool> buildTool(String id) {
            return buildTools.stream().filter(b -> b.id().equals(id)).findFirst();
        }
    }

    /** Como compilar, rodar e testar com uma ferramenta de build. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BuildTool(String id, String name, String icon, String build, String run, String test) {}

    /**
     * Pacote (dependência) ou plugin de um framework.
     *
     * @param artifact coordenadas separadas por vírgula (Maven {@code grupo:artefato[:versão]}, npm {@code nome@versão},
     *                 PyPI {@code nome>=versão})
     * @param needs "database" quando só faz sentido com banco
     * @param feature recurso do formulário que ativa o pacote (auth, openapi, tests)
     * @param buildTools ferramentas de build em que o plugin existe (vazio = todas)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pkg(String id, String name, String kind, String artifact, boolean required,
                      @JsonProperty("default") boolean selectedByDefault, String needs, String feature, String scope,
                      List<String> buildTools, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Database(String id, String name, String icon, String status, List<String> versions, String defaultVersion,
                           String description, String image, Integer port, Map<String, List<String>> packages) {

        public boolean available() {
            return AVAILABLE.equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvVar(String name, String example, boolean secret) {}

    /** Serviço opcional que complementa o banco central. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Connector(String id, String name, String icon, String description, String image, Integer port,
                            List<EnvVar> env, Map<String, List<String>> packages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiProvider(String id, String name, String icon, String status, String description, List<String> models,
                             String defaultModel, List<EnvVar> env, Map<String, List<String>> packages) {

        public boolean available() {
            return AVAILABLE.equals(status);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(String id, String name, String description, @JsonProperty("default") boolean enabledByDefault) {}

    public Optional<Stack> backend(String id) {
        return backends.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public Optional<Stack> frontend(String id) {
        return frontends.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public Optional<Database> database(String id) {
        return databases.stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public Optional<Connector> connector(String id) {
        return connectors.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public Optional<AiProvider> aiProvider(String id) {
        return aiProviders.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public Optional<Language> language(String id) {
        return languages.stream().filter(l -> l.id().equals(id)).findFirst();
    }
}
