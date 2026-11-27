package com.nova.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nova.constructor.catalog.Catalog;
import com.nova.constructor.copilot.CopilotAi;
import com.nova.constructor.copilot.CopilotDraft;
import com.nova.constructor.insight.ImprovementAi;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Funções com IA de ponta a ponta, com a IA trocada por um dublê que responde no formato do Claude. Cobre o caminho que
 * só roda com ANTHROPIC_API_KEY: Copilot, ajuda por campo, esboço, aplicar melhoria com IA e perguntar ao projeto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFeaturesIntegrationTest {

    static final AtomicReference<ImprovementAi.Request> lastRequest = new AtomicReference<>();

    @TestConfiguration
    static class FakeAi {

        @Bean
        @Primary
        CopilotAi fakeCopilot() {
            return new CopilotAi() {
                @Override public String model() { return "claude-falso"; }

                @Override
                public Optional<CopilotDraft> propose(String idea, String type, Catalog catalog) {
                    return Optional.of(new CopilotDraft("Clínica Sorriso", "Agenda de consultas odontológicas", idea, "WEB_APP", "spring-boot", "nextjs",
                            "postgresql", List.of(), false, List.of("auth", "tests"), List.of("Pacientes", "Consultas", "Nova consulta"),
                            List.of("O sistema deve permitir agendar consultas"), List.of(new CopilotDraft.Story("Agendar", "Como paciente, quero agendar")),
                            "Spring Boot e PostgreSQL pela agenda transacional.",
                            List.of(new CopilotDraft.EntityDraft("Paciente", List.of(new CopilotDraft.FieldDraft("nome", "TEXT", true, List.of()),
                                            new CopilotDraft.FieldDraft("telefone", "TEXT", false, List.of()))),
                                    new CopilotDraft.EntityDraft("Consulta", List.of(new CopilotDraft.FieldDraft("paciente", "TEXT", true, List.of()),
                                            new CopilotDraft.FieldDraft("data", "DATETIME", true, List.of()),
                                            new CopilotDraft.FieldDraft("status", "STATUS", true, List.of("AGENDADA", "CONFIRMADA", "CANCELADA"))))),
                            List.of(new CopilotDraft.ScreenDraft("Pacientes", "LIST", "Paciente", "AUTH"),
                                    new CopilotDraft.ScreenDraft("Consultas", "LIST", "Consulta", "AUTH"),
                                    new CopilotDraft.ScreenDraft("Nova consulta", "FORM", "Consulta", "PUBLIC"))));
                }

                @Override
                public Optional<String> assist(String field, String idea, String name, String current) {
                    return Optional.of("Agenda online para clínicas odontológicas.");
                }

                @Override
                public Optional<CopilotDraft.SketchDraft> sketch(byte[] image, String mediaType, String hint) {
                    return Optional.of(new CopilotDraft.SketchDraft(List.of(new CopilotDraft.ScreenDraft("Vitrine", "LIST", "Produto", "PUBLIC")),
                            List.of(new CopilotDraft.EntityDraft("Produto", List.of(new CopilotDraft.FieldDraft("nome", "TEXT", true, List.of())))),
                            List.of("Vitrine: título, grade de cards, botão comprar"), "Cores roxas; layout em grade."));
                }
            };
        }

        @Bean
        @Primary
        ImprovementAi fakeImprovementAi() {
            return new ImprovementAi() {
                @Override public String model() { return "claude-falso"; }

                @Override
                public Optional<Result> apply(Request request) {
                    lastRequest.set(request);
                    return Optional.of(new Result(List.of(new FileOut("docs/SEGURANCA.md", "# Segurança\n\n" + request.note() + "\n", "Documenta a decisão.")),
                            "Criei o guia de segurança pedido."));
                }

                @Override
                public Optional<Answer> ask(String question, List<String> tree, Map<String, String> files) {
                    return Optional.of(new Answer("O status fica na entidade e muda pelo endpoint de status.",
                            List.of(new Reference(files.keySet().iterator().next(), 1, "arquivo mais ligado à pergunta"))));
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;

    private String token() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        return "Bearer " + JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Pessoa IA","username":"ia_%s","email":"ia%s@nova.test","password":"Senha1234","birthDate":"1990-01-01","acceptTerms":true}
                        """.formatted(u, u))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void copilotComIaUsaEntidadesTelasEAjudaPorCampo() throws Exception {
        String auth = token();
        mvc.perform(get("/api/v1/copilot/status").header("Authorization", auth)).andExpect(jsonPath("$.provider").value("claude"))
                .andExpect(jsonPath("$.model").value("claude-falso"));
        mvc.perform(post("/api/v1/copilot/propose").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"Agenda online para uma clínica odontológica com login\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("claude"))
                .andExpect(jsonPath("$.spec.name").value("Clínica Sorriso"))
                .andExpect(jsonPath("$.spec.entities[*].name", hasItem("Consulta")))
                .andExpect(jsonPath("$.spec.entities[1].fields[2].values[0]").value("AGENDADA"))
                .andExpect(jsonPath("$.spec.screenSpecs[?(@.name=='Consultas')].access", hasItem("AUTH")))
                .andExpect(jsonPath("$.spec.requirements[0].text").value("O sistema deve permitir agendar consultas"));
        mvc.perform(post("/api/v1/copilot/assist").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"description\",\"name\":\"Clínica\"}"))
                .andExpect(jsonPath("$.source").value("claude")).andExpect(jsonPath("$.suggestion", containsString("clínicas")));
        mvc.perform(multipart("/api/v1/copilot/sketch").file(new MockMultipartFile("image", "tela.png", "image/png", new byte[] {1, 2, 3}))
                        .param("hint", "layout").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.screens[0].template").value("LIST"))
                .andExpect(jsonPath("$.entities[0].name").value("Produto")).andExpect(jsonPath("$.notes", containsString("grade")));
        mvc.perform(multipart("/api/v1/copilot/sketch").file(new MockMultipartFile("image", "a.pdf", "application/pdf", new byte[] {1}))
                .header("Authorization", auth)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void aplicarMelhoriaComIaEPerguntarAoProjeto() throws Exception {
        String auth = token();
        String id = JsonPath.read(mvc.perform(post("/api/v1/generator/projects").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sem login\",\"type\":\"APP\",\"backend\":{\"id\":\"spring-boot\"},\"entities\":[{\"name\":\"Nota\",\"fields\":[{\"name\":\"texto\"}]}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String insights = mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", auth)).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(insights, "$.aiAvailable")).isTrue();
        String auth2 = ((List<String>) JsonPath.read(insights, "$.proposals[?(@.kind=='auth')].id")).getFirst();

        String preview = mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + auth2 + "/preview").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseVersion\":1,\"useAi\":true,\"note\":\"Exigir login nas rotas de escrita\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changeSet.usedAi").value(true))
                .andExpect(jsonPath("$.changeSet.changes[0].path").value("docs/SEGURANCA.md")).andReturn().getResponse().getContentAsString();
        assertThat(lastRequest.get().note()).isEqualTo("Exigir login nas rotas de escrita");
        assertThat(lastRequest.get().files()).isNotEmpty();
        String content = JsonPath.read(preview, "$.changeSet.changes[0].after");
        assertThat(content).contains("Exigir login nas rotas de escrita");
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + auth2 + "/apply").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":1,\"useAi\":true,\"files\":[{\"path\":\"docs/SEGURANCA.md\",\"content\":" + tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(content) + "}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.usedAi").value(true));
        mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", auth)).andExpect(jsonPath("$.applied[0].usedAi").value(true));
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + auth2 + "/apply").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseVersion\":2,\"useAi\":true,\"files\":[{\"path\":\".env\",\"content\":\"X=1\"}]}"))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(post("/api/v1/projects/" + id + "/ask").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Onde fica a entidade Nota?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer", containsString("status")))
                .andExpect(jsonPath("$.references[0].path", containsString("Nota")));
    }
}
