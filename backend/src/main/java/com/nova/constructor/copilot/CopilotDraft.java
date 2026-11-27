package com.nova.constructor.copilot;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** Formato que o Claude devolve (saída estruturada) ao propor um projeto. Os ids vêm do catálogo. */
public record CopilotDraft(
        @JsonPropertyDescription("Nome curto do app, até 60 caracteres") String name,
        @JsonPropertyDescription("Descrição em uma frase, até 200 caracteres") String description,
        @JsonPropertyDescription("Objetivo do app em um parágrafo: para quem é e que problema resolve") String objective,
        @JsonPropertyDescription("WEB_APP ou APP") String type,
        @JsonPropertyDescription("Id do backend do catálogo") String backendId,
        @JsonPropertyDescription("Id do frontend do catálogo, ou vazio se o app não tiver frontend") String frontendId,
        @JsonPropertyDescription("Id do banco do catálogo, ou vazio se não precisar de banco") String databaseId,
        @JsonPropertyDescription("Ids dos conectores opcionais realmente necessários; lista vazia se nenhum") List<String> connectors,
        @JsonPropertyDescription("true se o app precisa de IA externa") boolean usesAi,
        @JsonPropertyDescription("Ids dos recursos do catálogo a ativar") List<String> features,
        @JsonPropertyDescription("Nomes das telas, na ordem de navegação, no máximo 12") List<String> screens,
        @JsonPropertyDescription("Requisitos funcionais, um por item") List<String> requirements,
        @JsonPropertyDescription("Backlog inicial") List<Story> backlog,
        @JsonPropertyDescription("Explicação curta das escolhas de tecnologia") String rationale,
        @JsonPropertyDescription("Dados que o app guarda: entidades no singular em PascalCase (ex.: Produto, Pedido), no máximo 8") List<EntityDraft> entities,
        @JsonPropertyDescription("Caracterização de cada tela listada em screens, na mesma ordem") List<ScreenDraft> screenDetails) {

    public record EntityDraft(
            @JsonPropertyDescription("Nome no singular, PascalCase, sem acentos") String name,
            @JsonPropertyDescription("Campos, sem o id (que é automático), no máximo 10") List<FieldDraft> fields) {}

    public record FieldDraft(
            @JsonPropertyDescription("Nome em camelCase, sem acentos (ex.: dataEntrega)") String name,
            @JsonPropertyDescription("TEXT, LONG_TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, DATETIME ou STATUS") String type,
            @JsonPropertyDescription("true se o campo é obrigatório") boolean required,
            @JsonPropertyDescription("Só para STATUS: valores em MAIÚSCULAS, o primeiro é o inicial; lista vazia nos outros tipos") List<String> values) {}

    public record ScreenDraft(
            @JsonPropertyDescription("Nome da tela, igual ao de screens") String name,
            @JsonPropertyDescription("LIST, DETAIL, FORM, DASHBOARD, LOGIN, SIGNUP, LANDING, SETTINGS ou BLANK") String template,
            @JsonPropertyDescription("Entidade mostrada (para LIST, DETAIL e FORM), ou vazio") String entity,
            @JsonPropertyDescription("PUBLIC ou AUTH (exige login)") String access) {}


    public record Story(
            @JsonPropertyDescription("Título curto da história") String title,
            @JsonPropertyDescription("História no formato: Como <pessoa>, quero <ação>, para <benefício>") String story) {}
}
