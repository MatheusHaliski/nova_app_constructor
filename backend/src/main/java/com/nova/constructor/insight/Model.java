package com.nova.constructor.insight;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contratos da análise do projeto: camadas, API, dados, telas, dependências, saúde e propostas de melhoria. */
public final class Model {

    private Model() {}

    /** Papel de um arquivo dentro da arquitetura. */
    public enum Role { CONTROLLER, SERVICE, REPOSITORY, ENTITY, CONFIG, TEST, PAGE, COMPONENT, OTHER }

    public record LayerItem(String name, String path, String detail) {}

    public record Layer(Role role, String label, String help, List<LayerItem> items) {}

    /** @param access PUBLIC, AUTHENTICATED ou a expressão de autorização encontrada (ex.: ADMIN) */
    public record Endpoint(String method, String path, String handler, String file, int line, String access) {}

    public enum Operation { LIST, READ, CREATE, UPDATE, DELETE }

    /** Recurso da API agrupado pelo caminho da coleção, com a matriz de operações CRUD e ações extras. */
    public record Resource(String path, String name, String entity, Map<Operation, Endpoint> operations, List<Endpoint> actions) {}

    public enum FieldKind { TEXT, LONG_TEXT, INTEGER, LONG, DECIMAL, DOUBLE, BOOLEAN, DATE, DATETIME, INSTANT, ENUM, UUID, RELATION, COLLECTION, OTHER }

    /** Campo de uma entidade. {@code enumType} e {@code enumValues} só para campos de enum. */
    public record Field(String name, String type, FieldKind kind, boolean required, boolean id, boolean writable, String enumType,
                        List<String> enumValues) {}

    /**
     * @param stack spring-boot, nestjs ou fastapi
     * @param idType tipo do identificador (Long, Integer, UUID, String…)
     * @param writable os campos podem ser alterados por setters, Lombok ou campos públicos
     */
    public record Entity(String name, String table, String file, String stack, String idType, boolean writable, List<Field> fields,
                         List<String> relations, String repository, String repositoryFile) {}

    public record Table(String name, String file, List<String> columns) {}

    public record Screen(String route, String file, String title, List<String> apiCalls, boolean handlesEmpty, boolean handlesError,
                         boolean generatedPlaceholder) {}

    public record EnvVar(String name, boolean secret, boolean documented, List<String> usedIn) {}

    public record Dependencies(String backendBuild, int backendPackages, int backendPlugins, String frontendBuild, int frontendPackages,
                               List<String> connectors, List<EnvVar> env) {}

    public enum StageStatus { PASS, WARN, FAIL, NOT_RUN, NOT_APPLICABLE }

    public record Stage(String id, String label, StageStatus status, String summary, List<String> proposals) {}

    public enum Severity { GAP, SUGGESTION }

    public enum Category { BUSINESS, TESTS, SECURITY, PRIVACY, CONFIG, SCREENS, REVIEW }

    public record Option(String value, String label) {}

    /**
     * Opção editável de uma proposta. Tipos: TEXT, SELECT, MULTI (vários de {@code options}), BOOLEAN, LIST (lista de textos),
     * FIELDS (lista de {name, type, required, values}) e ENV (lista de {name, value, include}).
     */
    public record Param(String name, String label, String type, Object value, List<Option> options, String help) {}

    /**
     * Melhoria sugerida pela análise.
     *
     * @param automatic o NOVA sabe aplicar sozinho, sem IA
     * @param howTo     o que fazer à mão quando não há aplicação automática
     */
    public record Proposal(String id, String kind, Category category, Severity severity, String part, String title, String why,
                           List<String> where, List<Param> params, boolean automatic, String howTo) {}

    /** Proposta como o usuário a vê: com a situação (aberta ou dispensada) e se a IA pode aplicá-la. */
    public record ProposalView(String id, String kind, Category category, Severity severity, String part, String title, String why,
                               List<String> where, List<Param> params, boolean automatic, boolean aiAvailable, String howTo, String state) {

        static ProposalView of(Proposal p, boolean ai, boolean dismissed) {
            return new ProposalView(p.id(), p.kind(), p.category(), p.severity(), p.part(), p.title(), p.why(), p.where(), p.params(),
                    p.automatic(), ai, p.howTo(), dismissed ? "DISMISSED" : "OPEN");
        }
    }

    public record Applied(String proposalId, String title, int version, boolean usedAi, Instant appliedAt) {}

    public record Stack(String backend, String backendLanguage, String backendBuild, String frontend, String database, boolean auth,
                        boolean jpa, boolean flyway) {}

    /** Resultado bruto da análise de uma versão. */
    public record Analysis(Stack stack, List<Layer> layers, List<Endpoint> endpoints, List<Resource> resources, List<Entity> entities,
                           List<Table> tables, List<Screen> screens, Dependencies dependencies, List<Stage> stages, List<Proposal> proposals,
                           int fileCount, int testCount, List<RequirementStatus> requirements, List<Flow> flows, List<PrivacyFinding> privacy,
                           List<A11yFinding> accessibility) {}

    /** Situação de um requisito do projeto, com o que no código o atende. */
    public record RequirementStatus(String text, String acceptance, String priority, String status, List<String> evidence, List<String> missing) {}

    /** Caminho de uma rota pelas camadas: telas que a chamam → controller → serviços → repositório → entidade → tabela. */
    public record Flow(String method, String path, List<String> screens, String controller, List<String> services, String repository, String entity,
                       String table, String access) {}

    /**
     * Dado pessoal encontrado numa entidade.
     *
     * @param kind IDENTIFICATION, CONTACT, LOCATION, BIRTH ou SENSITIVE (art. 5º, II)
     */
    public record PrivacyFinding(String entity, String field, String kind, boolean publicRead, List<String> articles, String advice) {}

    public record A11yFinding(String file, int line, String rule, String message) {}

    /** Caminho até publicar: etapas em ordem, com o que falta em cada uma. */
    public record Readiness(int percent, List<ReadinessStep> steps) {}

    public record ReadinessStep(String id, String label, boolean done, int pending, String next) {}

    /** Resposta de GET /insights. */
    public record Insights(int version, Instant analyzedAt, Stack stack, List<Stage> stages, List<Layer> layers, List<Resource> resources,
                           List<Endpoint> endpoints, List<Entity> entities, List<Table> tables, List<Screen> screens, Dependencies dependencies,
                           List<ProposalView> proposals, List<Applied> applied, boolean aiAvailable, int fileCount, int testCount,
                           List<RequirementStatus> requirements, List<Flow> flows, List<PrivacyFinding> privacy, List<A11yFinding> accessibility,
                           Readiness readiness) {}

    public enum ChangeAction { NEW, EDIT }

    public record FileChange(String path, ChangeAction action, String before, String after, String why) {}

    /** O que uma melhoria muda, antes de aplicar. */
    public record ChangeSet(List<FileChange> changes, List<String> notes, String summary, boolean usedAi) {}

    public record Preview(int baseVersion, int nextVersion, ChangeSet changeSet) {}

    public record ApplyResult(int version, List<String> paths, List<String> notes, boolean usedAi) {}

    public record PreviewPage(String route, String file, String title, List<String> apiCalls, boolean handlesEmpty, boolean handlesError) {}

    /** @param access quem pode chamar (PUBLIC, AUTHENTICATED…): a prévia usa para simular visitante e pessoa logada */
    public record Mock(String method, String path, Object body, String access) {}

    /** Material para a prévia das telas no navegador: código da versão (com as edições manuais) e respostas de exemplo da API. */
    public record PreviewBundle(int version, String framework, String root, List<PreviewPage> pages, Map<String, String> sources,
                                List<Mock> mocks, boolean truncated) {}
}
