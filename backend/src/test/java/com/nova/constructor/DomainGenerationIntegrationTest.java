package com.nova.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Dados do domínio no formulário: entidades, telas por modelo, requisitos e tema viram código, e o Copilot os propõe. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DomainGenerationIntegrationTest {

    private static final String SPEC = """
            {"name":"Agenda Clínica","type":"WEB_APP","backend":{"id":"spring-boot","packages":["data-jpa","flyway"]},"frontend":{"id":"nextjs"},
             "database":{"id":"postgresql"},"features":["auth","tests"],
             "entities":[{"name":"Paciente","fields":[{"name":"Nome completo","type":"TEXT","required":true},{"name":"email","type":"TEXT"}]},
                         {"name":"Consulta","fields":[{"name":"paciente","type":"TEXT","required":true},{"name":"data","type":"DATETIME","required":true},
                                                      {"name":"status","type":"STATUS","values":["agendada","confirmada","cancelada"]}]}],
             "screens":["Início","Pacientes","Consultas","Nova consulta","Consulta"],
             "screenSpecs":[{"name":"Pacientes","template":"LIST","entity":"Paciente"},{"name":"Consultas","template":"LIST","entity":"Consulta","access":"AUTH"},
                            {"name":"Nova consulta","template":"FORM","entity":"Consulta"},{"name":"Consulta","template":"DETAIL","entity":"Consulta"}],
             "requirements":[{"text":"Pacientes veem os horários","priority":"urgente"}],
             "theme":{"primary":"#0E7490","font":"Inter","radius":"SM"}}
            """;

    @Autowired
    MockMvc mvc;

    private String token() throws Exception {
        String u = UUID.randomUUID().toString().substring(0, 8);
        return "Bearer " + JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Dev Domínio","username":"dom_%s","email":"dom%s@nova.test","password":"Senha1234","birthDate":"1994-02-03","acceptTerms":true}
                        """.formatted(u, u))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void entidadesETelasViramCodigoLigado() throws Exception {
        String auth = token();
        String plan = mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(SPEC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.entities[0].fields[0].name").value("nomeCompleto"))
                .andExpect(jsonPath("$.spec.entities[1].fields[2].values[0]").value("AGENDADA"))
                .andExpect(jsonPath("$.spec.screens", not(hasItem("Início"))))
                .andExpect(jsonPath("$.spec.screenSpecs[?(@.name=='Consulta')].inMenu", hasItem(false)))
                .andExpect(jsonPath("$.spec.requirements[0].priority").value("IMPORTANT"))
                .andExpect(jsonPath("$.spec.theme.primary").value("#0E7490"))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("página inicial"))))
                .andReturn().getResponse().getContentAsString();
        List<String> files = JsonPath.read(plan, "$.files");
        assertThat(files).contains("backend/src/main/java/com/nova/agendaclinica/paciente/Paciente.java",
                "backend/src/main/java/com/nova/agendaclinica/consulta/ConsultaStatusController.java",
                "backend/src/test/java/com/nova/agendaclinica/consulta/ConsultaControllerTest.java",
                "backend/src/main/resources/db/migration/V2__criar_pacientes.sql",
                "backend/src/main/resources/db/migration/V3__criar_consultas.sql",
                "frontend/src/app/pacientes/page.tsx", "frontend/src/app/nova-consulta/page.tsx", "frontend/src/app/consulta/[id]/page.tsx")
                .doesNotContain("frontend/src/app/consulta/page.tsx");

        String id = JsonPath.read(mvc.perform(post("/api/v1/generator/projects").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(SPEC)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String migration = JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", "backend/src/main/resources/db/migration/V3__criar_consultas.sql")
                .header("Authorization", auth)).andReturn().getResponse().getContentAsString(), "$.content");
        assertThat(migration).contains("GENERATED BY DEFAULT AS IDENTITY", "data TIMESTAMP(6) NOT NULL");
        String security = JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", "backend/src/main/java/com/nova/agendaclinica/security/SecurityConfig.java")
                .header("Authorization", auth)).andReturn().getResponse().getContentAsString(), "$.content");
        assertThat(security).contains("HttpMethod.GET, \"/api/pacientes\"").contains("HttpMethod.GET, \"/api/consultas\"");
        String css = JsonPath.read(mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", "frontend/src/app/globals.css")
                .header("Authorization", auth)).andReturn().getResponse().getContentAsString(), "$.content");
        assertThat(css).contains("--primary: #0E7490", "--radius: 4px", "family=Inter");
        // A análise do projeto gerado não acusa lacuna de regra de negócio.
        mvc.perform(get("/api/v1/projects/" + id + "/insights").header("Authorization", auth))
                .andExpect(jsonPath("$.stages[?(@.id=='business')].status", hasItem("PASS")))
                .andExpect(jsonPath("$.entities[*].name", hasItem("Consulta")));
    }

    @Test
    void dadosInvalidosSaoRecusadosComExplicacao() throws Exception {
        String auth = token();
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"X","type":"APP","backend":{"id":"spring-boot"},"entities":[{"name":"Status","fields":[{"name":"a"}]}]}"""))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("reservado")));
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"X","type":"WEB_APP","backend":{"id":"spring-boot"},"frontend":{"id":"nextjs"},
                         "entities":[{"name":"Item","fields":[{"name":"nome"}]}],"screenSpecs":[{"name":"Lista","template":"LIST","entity":"Produto"}]}"""))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("Produto")));
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"X","type":"APP","backend":{"id":"spring-boot"},"entities":[{"name":"Item","fields":[{"name":"s","type":"STATUS","values":["A"]}]}]}"""))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("dois valores")));
    }

    @Test
    void copilotPorRegrasPropoeEntidadesTelasERequisitos() throws Exception {
        mvc.perform(post("/api/v1/copilot/propose").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"Uma loja online de produtos naturais com login de clientes e pedidos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.entities[*].name", hasItem("Produto")))
                .andExpect(jsonPath("$.spec.entities[*].name", hasItem("Pedido")))
                .andExpect(jsonPath("$.spec.screenSpecs[?(@.name=='Detalhe do produto')].template", hasItem("DETAIL")))
                .andExpect(jsonPath("$.spec.screenSpecs[?(@.name=='Login')].template", hasItem("LOGIN")))
                .andExpect(jsonPath("$.spec.requirements[?(@.priority=='ESSENTIAL')].acceptance", hasItem(containsString("Produto"))));
    }
}
