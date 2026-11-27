package com.nova.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Visualizador do projeto: análise, propostas de melhoria editáveis, aplicação como nova versão, dispensa e material da prévia. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsightsIntegrationTest {

    private static final String SPEC = """
            {"name":"Loja Açaí","description":"Pedidos de açaí","type":"WEB_APP",
             "backend":{"id":"spring-boot","packages":["data-jpa","flyway"]},"frontend":{"id":"nextjs"},
             "database":{"id":"mysql"},"features":["auth","tests","docker"],"screens":["Produtos","Meus pedidos","Login"]}
            """;

    @Autowired
    MockMvc mvc;

    private String auth;
    private String id;

    private void setUp() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        String json = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Dev Insights","username":"ins_%s","email":"ins%s@nova.test","password":"Senha1234","birthDate":"1994-02-03","acceptTerms":true}
                        """.formatted(u, u)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        auth = "Bearer " + JsonPath.read(json, "$.accessToken");
        id = JsonPath.read(mvc.perform(post("/api/v1/generator/projects").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(SPEC))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String insights() throws Exception {
        return mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String proposalId(String json, String kind, String titlePart) {
        List<String> ids = JsonPath.read(json, "$.proposals[?(@.kind=='" + kind + "' && @.title =~ /.*" + titlePart + ".*/)].id");
        assertThat(ids).as("proposta %s com '%s'", kind, titlePart).hasSize(1);
        return ids.getFirst();
    }

    private String body(int base, String params) {
        return "{\"baseVersion\":" + base + ",\"params\":" + params + "}";
    }

    @Test
    void analiseMostraCamadasApiTelasSaudeEPropostas() throws Exception {
        setUp();
        String json = insights();
        assertThat((Integer) JsonPath.read(json, "$.version")).isEqualTo(1);
        assertThat((List<String>) JsonPath.read(json, "$.layers[*].role")).contains("CONTROLLER", "CONFIG", "TEST", "PAGE");
        assertThat((List<String>) JsonPath.read(json, "$.endpoints[*].path")).contains("/api/status", "/api/auth/token");
        assertThat((List<String>) JsonPath.read(json, "$.endpoints[?(@.path=='/api/status')].access")).containsExactly("PUBLIC");
        assertThat((List<String>) JsonPath.read(json, "$.screens[*].route")).contains("/produtos", "/meus-pedidos", "/login");
        assertThat((List<String>) JsonPath.read(json, "$.stages[*].id")).contains("structure", "build", "tests", "security", "business", "screens", "config");
        assertThat((List<String>) JsonPath.read(json, "$.stages[?(@.id=='build')].status")).containsExactly("NOT_RUN");
        assertThat((List<String>) JsonPath.read(json, "$.stages[?(@.id=='business')].status")).containsExactly("FAIL");
        assertThat((List<String>) JsonPath.read(json, "$.tables[*].name")).contains("app_setting");
        // Telas de dados viram propostas de API; Login não.
        proposalId(json, "resource", "Produtos");
        proposalId(json, "resource", "Meus pedidos");
        assertThat((List<String>) JsonPath.read(json, "$.proposals[?(@.kind=='resource')].title")).noneMatch(t -> t.contains("Login"));
        proposalId(json, "ci", "CI");
        assertThat((List<String>) JsonPath.read(json, "$.proposals[?(@.kind=='docker')].id")).isEmpty();
        assertThat((Boolean) JsonPath.read(json, "$.aiAvailable")).isFalse();
        // Dono só: outra conta não vê.
        String other = UUID.randomUUID().toString().substring(0, 8);
        String otherAuth = "Bearer " + JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Outra","username":"out_%s","email":"out%s@nova.test","password":"Senha1234","birthDate":"1994-02-03","acceptTerms":true}
                        """.formatted(other, other))).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", otherAuth)).andExpect(status().isNotFound());
    }

    @Test
    void criarApiDeUmaTelaEDepoisPermitirMudarOStatus() throws Exception {
        setUp();
        String pid = proposalId(insights(), "resource", "Meus pedidos");

        // Opções inválidas são recusadas com a explicação.
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + pid + "/preview").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(1, "{\"entity\":\"pedido\"}")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("maiúscula")));
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + pid + "/preview").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(1, "{\"fields\":[{\"name\":\"status\",\"type\":\"STATUS\",\"values\":[\"UM\"]}]}")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("dois valores")));
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + pid + "/preview").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(9, "{}")))
                .andExpect(status().isConflict());

        // Prévia com campos editados: nada é gravado ainda.
        String params = """
                {"entity":"Pedido","path":"/api/pedidos","readAccess":"PUBLIC","writeAccess":"AUTHENTICATED",
                 "fields":[{"name":"cliente","type":"TEXT","required":true},{"name":"total","type":"DECIMAL","required":false},
                           {"name":"status","type":"STATUS","values":["CRIADO","PAGO","ENVIADO","ENTREGUE","CANCELADO"]}],
                 "operations":["LIST","READ","CREATE","UPDATE","DELETE"],"tests":true,"page":true,"route":"/meus-pedidos"}
                """;
        String preview = mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + pid + "/preview").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(1, params)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nextVersion").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> paths = JsonPath.read(preview, "$.changeSet.changes[*].path");
        assertThat(paths).contains("backend/src/main/java/com/nova/lojaacai/pedido/Pedido.java",
                "backend/src/main/java/com/nova/lojaacai/pedido/PedidoRepository.java",
                "backend/src/main/java/com/nova/lojaacai/pedido/PedidoController.java",
                "backend/src/test/java/com/nova/lojaacai/pedido/PedidoControllerTest.java",
                "backend/src/main/resources/db/migration/V2__criar_pedidos.sql",
                "backend/src/main/java/com/nova/lojaacai/security/SecurityConfig.java",
                "frontend/src/app/meus-pedidos/page.tsx");
        String controller = JsonPath.<List<String>>read(preview, "$.changeSet.changes[?(@.path=~/.*PedidoController.java/)].after").getFirst();
        assertThat(controller).contains("@RequestMapping(\"/api/pedidos\")", "public record PedidoRequest(String cliente, BigDecimal total)",
                "throw invalido(\"Informe cliente.\")", "@DeleteMapping(\"/{id}\")");
        String security = JsonPath.<List<String>>read(preview, "$.changeSet.changes[?(@.path=~/.*SecurityConfig.java/)].after").getFirst();
        assertThat(security).contains(".requestMatchers(HttpMethod.GET, \"/api/pedidos\").permitAll()", "import org.springframework.http.HttpMethod;");
        String migration = JsonPath.<List<String>>read(preview, "$.changeSet.changes[?(@.path=~/.*V2__criar_pedidos.sql/)].after").getFirst();
        assertThat(migration).contains("CREATE TABLE pedidos", "id BIGINT NOT NULL AUTO_INCREMENT", "status VARCHAR(30) NOT NULL");
        mvc.perform(get("/api/v1/projects/" + id).header("Authorization", auth)).andExpect(jsonPath("$.currentVersion").value(1));

        // Aplicar cria a versão 2 e registra a melhoria.
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + pid + "/apply").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(1, params)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.usedAi").value(false));
        mvc.perform(get("/api/v1/projects/" + id + "/versions").header("Authorization", auth))
                .andExpect(jsonPath("$[0].origin").value("IMPROVEMENT")).andExpect(jsonPath("$[0].label", containsString("Melhoria")));

        // A nova análise enxerga a API e sugere o próximo passo: mudar o status do pedido.
        String after = insights();
        assertThat((List<String>) JsonPath.read(after, "$.applied[*].proposalId")).containsExactly(pid);
        assertThat((List<String>) JsonPath.read(after, "$.proposals[?(@.kind=='resource')].title")).noneMatch(t -> t.contains("pedidos"));
        assertThat((List<String>) JsonPath.read(after, "$.resources[?(@.path=='/api/pedidos')].entity")).containsExactly("Pedido");
        assertThat((List<String>) JsonPath.read(after, "$.entities[?(@.name=='Pedido')].table")).containsExactly("pedidos");
        assertThat((List<String>) JsonPath.read(after, "$.endpoints[?(@.path=='/api/pedidos' && @.method=='GET')].access")).containsExactly("PUBLIC");
        assertThat((List<String>) JsonPath.read(after, "$.endpoints[?(@.path=='/api/pedidos' && @.method=='POST')].access")).containsExactly("AUTHENTICATED");
        assertThat((List<Boolean>) JsonPath.read(after, "$.screens[?(@.route=='/meus-pedidos')].handlesEmpty")).containsExactly(true);
        String statusId = proposalId(after, "status", "status de Pedido");

        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + statusId + "/preview").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(2, "{\"values\":[\"INEXISTENTE\"]}")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("não existe")));
        String statusParams = "{\"method\":\"PATCH\",\"path\":\"/api/pedidos/{id}/status\",\"values\":[\"PAGO\",\"ENVIADO\",\"CANCELADO\"],\"tests\":true}";
        String statusPreview = mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + statusId + "/apply").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(body(2, statusParams)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(statusPreview, "$.paths")).contains(
                "backend/src/main/java/com/nova/lojaacai/pedido/PedidoStatusController.java",
                "backend/src/test/java/com/nova/lojaacai/pedido/PedidoStatusControllerTest.java");
        String file = JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path",
                "backend/src/main/java/com/nova/lojaacai/pedido/PedidoStatusController.java").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString(), "$.content");
        assertThat(file).contains("EnumSet.of(Pedido.Status.PAGO, Pedido.Status.ENVIADO, Pedido.Status.CANCELADO)", "@PatchMapping(\"/api/pedidos/{id}/status\")");
        String finalJson = insights();
        assertThat((List<String>) JsonPath.read(finalJson, "$.proposals[*].kind")).doesNotContain("status");
        assertThat((List<String>) JsonPath.read(finalJson, "$.endpoints[?(@.method=='PATCH')].path")).containsExactly("/api/pedidos/{id}/status");
    }

    @Test
    void dispensarRestaurarIaPreviaDasTelasECorrecoes() throws Exception {
        setUp();
        String json = insights();
        String ci = proposalId(json, "ci", "CI");
        mvc.perform(put("/api/v1/projects/" + id + "/improvements/" + ci + "/dismissal").header("Authorization", auth)).andExpect(status().isNoContent());
        assertThat((List<String>) JsonPath.read(insights(), "$.proposals[?(@.id=='" + ci + "')].state")).containsExactly("DISMISSED");
        mvc.perform(delete("/api/v1/projects/" + id + "/improvements/" + ci + "/dismissal").header("Authorization", auth)).andExpect(status().isNoContent());
        assertThat((List<String>) JsonPath.read(insights(), "$.proposals[?(@.id=='" + ci + "')].state")).containsExactly("OPEN");

        // Sem chave configurada, a IA é recusada com explicação.
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + ci + "/preview").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseVersion\":1,\"useAi\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("IA")));

        // CI gerado a partir da stack do projeto.
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + ci + "/apply").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paths", hasItem(".github/workflows/ci.yml")));

        // Variável lida pelo código e não documentada vira proposta; aplicar documenta no .env.example.
        String yml = JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", "backend/src/main/resources/application.yml")
                .header("Authorization", auth)).andReturn().getResponse().getContentAsString(), "$.content");
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"backend/src/main/resources/application.yml\",\"baseVersion\":2,\"content\":"
                                + tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(yml + "loja:\n  frete: ${FRETE_GRATIS_ACIMA:100}\n  senha: SegredoMuitoForte123\n") + "}"))
                .andExpect(status().isOk());
        String v3 = insights();
        String env = proposalId(v3, "env", "variável");
        String secret = proposalId(v3, "secret", "application.yml");
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + secret + "/apply").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseVersion\":3}"))
                .andExpect(status().isOk());
        String fixed = JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", "backend/src/main/resources/application.yml")
                .header("Authorization", auth)).andReturn().getResponse().getContentAsString(), "$.content");
        assertThat(fixed).contains("senha: ${LOJA_SENHA}").doesNotContain("SegredoMuitoForte123");
        String v4 = insights();
        assertThat((List<String>) JsonPath.read(v4, "$.proposals[*].kind")).doesNotContain("secret");
        String env4 = proposalId(v4, "env", "variável");
        mvc.perform(post("/api/v1/projects/" + id + "/improvements/" + env4 + "/apply").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"baseVersion\":4}"))
                .andExpect(status().isOk());
        String example = JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", ".env.example")
                .header("Authorization", auth)).andReturn().getResponse().getContentAsString(), "$.content");
        assertThat(example).contains("FRETE_GRATIS_ACIMA=", "LOJA_SENHA=");
        assertThat(env).isNotBlank();

        // Prévia das telas: código da versão atual (com as edições) e respostas de exemplo.
        mvc.perform(get("/api/v1/projects/" + id + "/preview-bundle").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.framework").value("nextjs"))
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.pages[*].route", hasItem("/produtos")))
                .andExpect(jsonPath("$.sources['frontend/src/app/page.tsx']").exists())
                .andExpect(jsonPath("$.sources['backend/pom.xml']").doesNotExist())
                .andExpect(jsonPath("$.mocks[?(@.path=='/api/status')].body.status", hasItem("ok")))
                .andExpect(jsonPath("$.mocks[*].path", not(hasItem("/api/auth/login"))));
    }
}
