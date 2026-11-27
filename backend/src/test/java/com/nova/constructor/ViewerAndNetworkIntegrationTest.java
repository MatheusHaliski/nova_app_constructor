package com.nova.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Janelas novas do visualizador, rede (remix, sugestões, perfil, fixados), painel inicial e campos novos da conta. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewerAndNetworkIntegrationTest {

    private static final String SPEC = """
            {"name":"Clínica %s","type":"WEB_APP","backend":{"id":"spring-boot","version":"4.0.9","packages":["data-jpa","flyway"]},"frontend":{"id":"nextjs"},
             "database":{"id":"mysql"},"features":["auth","tests"],
             "entities":[{"name":"Paciente","fields":[{"name":"nome","type":"TEXT","required":true},{"name":"cpf","type":"TEXT"},{"name":"diagnostico","type":"LONG_TEXT"}]}],
             "screens":["Pacientes"],"screenSpecs":[{"name":"Pacientes","template":"LIST","entity":"Paciente"}],
             "requirements":[{"text":"O sistema deve permitir listar, criar e excluir pacientes","priority":"ESSENTIAL"},
                             {"text":"O sistema deve emitir nota fiscal","priority":"LATER"}]}
            """;

    @Autowired
    MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    private record Account(String auth, String username) {}

    private Account account(String agent) throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/v1/auth/register").header("User-Agent", agent).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Pessoa %s","username":"v_%s","email":"v%s@nova.test","password":"Senha1234","birthDate":"1990-01-01","acceptTerms":true}
                        """.formatted(u, u, u))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account("Bearer " + JsonPath.read(body, "$.accessToken"), "v_" + u);
    }

    private String project(Account a, String spec) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/generator/projects").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON).content(spec))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String file(Account a, String id, String path) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", path).header("Authorization", a.auth()))
                .andReturn().getResponse().getContentAsString(), "$.content");
    }

    private void save(Account a, String id, String path, String content, int base) throws Exception {
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"" + path + "\",\"baseVersion\":" + base + ",\"content\":" + json.writeValueAsString(content) + "}")).andExpect(status().isOk());
    }

    @Test
    void janelasRequisitosFluxosPrivacidadeAcessibilidadeECaminhoAtePublicar() throws Exception {
        Account a = account("Mozilla/5.0 (X11; Linux x86_64) Chrome/140.0");
        String id = project(a, SPEC.formatted("A"));
        String page = "frontend/src/app/pacientes/page.tsx";
        save(a, id, page, file(a, id, page).replace("<h1>", "<img src=\"/logo.png\" />\n      <h1>"), 1);
        String insights = mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", a.auth())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(insights, "$.requirements[?(@.text=~/.*pacientes/)].status")).containsExactly("COVERED");
        assertThat((List<String>) JsonPath.read(insights, "$.requirements[?(@.text=~/.*nota fiscal/)].status")).containsExactly("UNKNOWN");
        assertThat((List<String>) JsonPath.read(insights, "$.flows[?(@.path=='/api/pacientes' && @.method=='GET')].table")).containsExactly("pacientes");
        assertThat((List<String>) JsonPath.read(insights, "$.flows[?(@.path=='/api/pacientes' && @.method=='GET')].screens[*]")).contains("/pacientes");
        assertThat((List<String>) JsonPath.read(insights, "$.privacy[?(@.field=='diagnostico')].kind")).containsExactly("SENSITIVE");
        assertThat((List<String>) JsonPath.read(insights, "$.privacy[?(@.field=='cpf')].articles[*]")).contains("Art. 46");
        assertThat((List<String>) JsonPath.read(insights, "$.proposals[?(@.kind=='privacy')].title")).containsExactly("Proteger os dados pessoais de Paciente");
        assertThat((List<String>) JsonPath.read(insights, "$.accessibility[?(@.rule=='img-alt')].file")).containsExactly(page);
        assertThat((List<String>) JsonPath.read(insights, "$.stages[?(@.id=='privacy')].status")).containsExactly("FAIL");
        assertThat((Integer) JsonPath.read(insights, "$.readiness.percent")).isBetween(0, 99);
        assertThat((List<Boolean>) JsonPath.read(insights, "$.readiness.steps[?(@.id=='privacy')].done")).containsExactly(false);

        // Máquina do tempo: o que mudou da versão 1 para a 2.
        mvc.perform(get("/api/v1/projects/" + id + "/compare").param("from", "1").param("to", "2").header("Authorization", a.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files", hasSize(1))).andExpect(jsonPath("$.files[0].change").value("CHANGED"))
                .andExpect(jsonPath("$.files[0].added").value(1)).andExpect(jsonPath("$.files[0].diff", containsString("+       <img src=\"/logo.png\" />")));

        // Perguntar ao projeto exige a IA.
        mvc.perform(post("/api/v1/projects/" + id + "/ask").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Onde fica a regra do CPF?\"}")).andExpect(status().isConflict());
    }

    @Test
    void previaDaPropostaCompararStacksEEsboco() throws Exception {
        Account a = account("curl/8");
        String id = project(a, """
                {"name":"Loja","type":"WEB_APP","backend":{"id":"spring-boot","packages":["data-jpa"]},"frontend":{"id":"nextjs"},"database":{"id":"mysql"},
                 "screens":["Produtos"]}""");
        String insights = mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", a.auth())).andReturn().getResponse().getContentAsString();
        String pid = ((List<String>) JsonPath.read(insights, "$.proposals[?(@.kind=='resource')].id")).getFirst();
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + pid + "/preview-bundle").header("Authorization", a.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.sources['frontend/src/app/produtos/page.tsx']", containsString("api<Produto[]>(\"/api/produtos\")")))
                .andExpect(jsonPath("$.mocks[?(@.path=='/api/produtos')].body[0].nome", hasItem(containsString("exemplo"))));
        mvc.perform(get("/api/v1/projects/" + id).header("Authorization", a.auth())).andExpect(jsonPath("$.currentVersion").value(1));

        mvc.perform(post("/api/v1/generator/compare").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Loja","type":"APP","backend":{"id":"spring-boot"},"entities":[{"name":"Produto","fields":[{"name":"nome"}]}]}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.backend=='spring-boot')].entitiesAsCode", hasItem(true)))
                .andExpect(jsonPath("$[?(@.backend=='fastapi')].requires", hasItem(containsString("Python"))))
                .andExpect(jsonPath("$[?(@.backend=='nestjs')].backendFiles", hasItem(org.hamcrest.Matchers.greaterThan(5))));

        mvc.perform(multipart("/api/v1/copilot/sketch").file(new MockMultipartFile("image", "tela.png", "image/png", new byte[] {1, 2, 3}))
                .header("Authorization", a.auth())).andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("IA")));
    }

    @Test
    void remixSugestaoDeOutraPessoaPerfilPublicoEFixados() throws Exception {
        Account owner = account("Mozilla/5.0 (Windows NT 10.0) Firefox/140.0");
        Account other = account("Mozilla/5.0 (iPhone) Safari/604.1");
        String id = project(owner, SPEC.formatted("B"));
        mvc.perform(put("/api/v1/projects/" + id + "/sharing").header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"COMMUNITY\",\"license\":\"MIT\",\"currentPassword\":\"Senha1234\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/community/projects/" + id + "/remix").header("Authorization", other.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.spec.name").value("Remix de Clínica B"))
                .andExpect(jsonPath("$.origin", containsString(owner.username())));

        String readme = "# Clínica B\n\nComo rodar: veja o .env.example.\n";
        mvc.perform(post("/api/v1/community/projects/" + id + "/suggestions").header("Authorization", other.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Melhorar o README\",\"body\":\"Explica como rodar.\",\"files\":[{\"path\":\"README.md\",\"content\":" + json.writeValueAsString(readme) + "}]}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/community/projects/" + id + "/suggestions").header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"body\":\"y\"}")).andExpect(status().isConflict());
        String insights = mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", owner.auth())).andReturn().getResponse().getContentAsString();
        String review = ((List<String>) JsonPath.read(insights, "$.proposals[?(@.kind=='review')].id")).getFirst();
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + review + "/apply").header("Authorization", owner.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"baseVersion\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(file(owner, id, "README.md")).isEqualTo(readme);
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", other.auth()))
                .andExpect(jsonPath("$.items[*].message", hasItem(containsString("foi aplicada"))));

        // Perfil privado não aparece para outras pessoas; na comunidade, mostra projetos, fixados e contribuições.
        mvc.perform(get("/api/v1/people/" + owner.username()).header("Authorization", other.auth())).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/me/privacy").header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileVisibility\":\"COMMUNITY\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/me/pinned-projects").header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectIds\":[\"" + id + "\"]}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/people/" + owner.username()).header("Authorization", other.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pinned[0].id").value(id)).andExpect(jsonPath("$.stacks", hasItem("spring-boot")))
                .andExpect(jsonPath("$.mine").value(false));
        mvc.perform(get("/api/v1/people/" + other.username()).header("Authorization", other.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contributions").value(1)).andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void painelSessoesExportacaoECamposNovosDaConta() throws Exception {
        Account a = account("Mozilla/5.0 (Macintosh; Mac OS X) Safari/605.1");
        String id = project(a, SPEC.formatted("C"));
        mvc.perform(get("/api/v1/projects/" + id).header("Authorization", a.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/dashboard").header("Authorization", a.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recent[0].id").value(id)).andExpect(jsonPath("$.recent[0].openProposals").isNumber())
                .andExpect(jsonPath("$.updates[0].technology").value("Spring Boot")).andExpect(jsonPath("$.updates[0].available").value("4.1.2"));

        String email = JsonPath.read(mvc.perform(get("/api/v1/me").header("Authorization", a.auth())).andReturn().getResponse().getContentAsString(), "$.email");
        mvc.perform(post("/api/v1/auth/login").header("User-Agent", "Mozilla/5.0 (Android 15) Chrome/140.0").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Senha1234\"}")).andExpect(status().isOk());
        String sessions = mvc.perform(get("/api/v1/me/sessions").header("Authorization", a.auth())).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[*].device", hasItem("Chrome no Android")))
                .andExpect(jsonPath("$[?(@.current==true)].device", hasItem("Safari no macOS"))).andReturn().getResponse().getContentAsString();
        String other = ((List<String>) JsonPath.read(sessions, "$[?(@.current==false)].id")).getFirst();
        mvc.perform(delete("/api/v1/me/sessions/" + other).header("Authorization", a.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/sessions").header("Authorization", a.auth())).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(put("/api/v1/me/consents/PRODUCT_ANALYTICS").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"granted\":true}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/me/account").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Pessoa C","username":"%s","birthDate":"1990-01-01","websiteUrl":"https://exemplo.dev","githubUsername":"pessoa-c"}"""
                        .formatted(JsonPath.read(mvc.perform(get("/api/v1/me").header("Authorization", a.auth())).andReturn().getResponse().getContentAsString(), "$.username").toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.websiteUrl").value("https://exemplo.dev"))
                .andExpect(jsonPath("$.account.lastLoginAt").exists());
        mvc.perform(get("/api/v1/me/export").header("Authorization", a.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.projects[0].name").value("Clínica C"))
                .andExpect(jsonPath("$.consentHistory[0].policyVersion").exists()).andExpect(jsonPath("$.consentHistory[0].channel").value("SETTINGS"))
                .andExpect(jsonPath("$.sessions", hasSize(1)));

        mvc.perform(put("/api/v1/projects/" + id + "/sharing").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"LINK\",\"currentPassword\":\"Senha1234\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/" + id + "/sharing/links").header("Authorization", a.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresInDays\":7,\"label\":\"Para a cliente\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/projects/" + id + "/sharing").header("Authorization", a.auth())).andExpect(jsonPath("$.links[0].label").value("Para a cliente"));
    }
}
