package com.nova.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Fase 4: visibilidade, verificações antes de publicar, links, rede, fork, comentários, notificações e moderação. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityIntegrationTest {

    private static final String SPEC = """
            {"name":"%s","description":"Agenda de consultas","type":"WEB_APP",
             "backend":{"id":"spring-boot","packages":["data-jpa","flyway"]},"frontend":{"id":"nextjs"},
             "database":{"id":"mysql"},"features":["auth","docker"],"screens":["Agenda"]}
            """;

    @Autowired
    MockMvc mvc;

    private record Account(String auth, String username) {}

    private Account account() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String json = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Dev Rede","username":"rede_%s","email":"rede%s@nova.test","password":"Senha1234","birthDate":"1994-02-03","acceptTerms":true}
                        """.formatted(id, id)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Account("Bearer " + JsonPath.read(json, "$.accessToken"), "rede_" + id);
    }

    private String admin() throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@nova.test\",\"password\":\"Admin12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.accessToken");
    }

    private String project(Account owner, String name) throws Exception {
        String json = mvc.perform(post("/api/v1/generator/projects").header("Authorization", owner.auth())
                        .contentType(MediaType.APPLICATION_JSON).content(SPEC.formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private MockHttpServletRequestBuilder share(Account owner, String id, String body) {
        return put("/api/v1/projects/" + id + "/sharing").header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void publish(Account owner, String id) throws Exception {
        mvc.perform(share(owner, id, "{\"visibility\":\"COMMUNITY\",\"license\":\"MIT\",\"currentPassword\":\"Senha1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("COMMUNITY"));
    }

    private int version(Account owner, String id) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/projects/" + id).header("Authorization", owner.auth()))
                .andReturn().getResponse().getContentAsString(), "$.currentVersion");
    }

    private void save(Account owner, String id, String path, String content) throws Exception {
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", owner.auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"%s\",\"content\":\"%s\",\"baseVersion\":%d}".formatted(path, content, version(owner, id))))
                .andExpect(status().isOk());
    }

    @Test
    void projetoNascePrivadoEAmpliarVisibilidadePassaPelasVerificacoes() throws Exception {
        Account owner = account();
        Account other = account();
        String id = project(owner, "Agenda Privada");

        mvc.perform(get("/api/v1/projects/" + id + "/sharing").header("Authorization", owner.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"));
        mvc.perform(get("/api/v1/community/projects/" + id).header("Authorization", other.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + id + "/sharing").header("Authorization", other.auth())).andExpect(status().isNotFound());

        // sem senha, sem licença, sem nome digitado
        mvc.perform(share(owner, id, "{\"visibility\":\"COMMUNITY\",\"license\":\"MIT\"}")).andExpect(status().isForbidden());
        mvc.perform(share(owner, id, "{\"visibility\":\"COMMUNITY\",\"license\":\"MIT\",\"currentPassword\":\"errada\"}")).andExpect(status().isForbidden());
        mvc.perform(share(owner, id, "{\"visibility\":\"COMMUNITY\",\"currentPassword\":\"Senha1234\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(share(owner, id, "{\"visibility\":\"PUBLIC\",\"license\":\"MIT\",\"currentPassword\":\"Senha1234\",\"confirmName\":\"outro\"}"))
                .andExpect(status().isUnprocessableContent());

        // segredo em arquivo visível bloqueia; ocultando o arquivo, passa
        save(owner, id, "backend/chaves.txt", "password = MinhaSenha12345");
        mvc.perform(share(owner, id, "{\"visibility\":\"COMMUNITY\",\"license\":\"MIT\",\"currentPassword\":\"Senha1234\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.detail", containsString("backend/chaves.txt")));
        mvc.perform(share(owner, id, "{\"visibility\":\"PUBLIC\",\"license\":\"MIT\",\"currentPassword\":\"Senha1234\",\"confirmName\":\"Agenda Privada\",\"hiddenPaths\":[\"backend/chaves.txt\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PUBLIC")).andExpect(jsonPath("$.hiddenPaths", hasItem("backend/chaves.txt")));

        mvc.perform(get("/api/v1/community/projects/" + id + "/tree").header("Authorization", other.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].path", hasItem("backend/pom.xml")))
                .andExpect(jsonPath("$[*].path", not(hasItem("backend/chaves.txt"))));
        mvc.perform(get("/api/v1/community/projects/" + id + "/file").param("path", "backend/chaves.txt").header("Authorization", other.auth()))
                .andExpect(status().isNotFound());

        // reduzir não pede senha e tira da rede
        mvc.perform(share(owner, id, "{\"visibility\":\"PRIVATE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/community/projects/" + id).header("Authorization", other.auth())).andExpect(status().isNotFound());
    }

    @Test
    void linkDeCompartilhamentoFuncionaSemLoginEPodeSerRevogado() throws Exception {
        Account owner = account();
        String id = project(owner, "Agenda Link");
        mvc.perform(post("/api/v1/projects/" + id + "/sharing/links").header("Authorization", owner.auth())).andExpect(status().isConflict());
        mvc.perform(share(owner, id, "{\"visibility\":\"LINK\",\"currentPassword\":\"Senha1234\"}")).andExpect(status().isOk());

        String created = mvc.perform(post("/api/v1/projects/" + id + "/sharing/links").header("Authorization", owner.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expiresInDays\":7}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").exists()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(created, "$.token");
        String linkId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/shared/" + token)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Agenda Link"))
                .andExpect(jsonPath("$.author").value(owner.username()));
        mvc.perform(get("/api/v1/shared/" + token + "/tree")).andExpect(status().isOk()).andExpect(jsonPath("$[*].path", hasItem("README.md")));
        mvc.perform(get("/api/v1/shared/" + token + "/file").param("path", "README.md")).andExpect(status().isOk());
        byte[] zip = mvc.perform(get("/api/v1/shared/" + token + "/download")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(entries(zip)).isNotEmpty().noneMatch(e -> e.endsWith("/.env"));
        mvc.perform(get("/api/v1/shared/token-inventado")).andExpect(status().isNotFound());

        // visibilidade "Link" não aparece na rede; o token não fica na listagem
        Account other = account();
        mvc.perform(get("/api/v1/community/projects/" + id).header("Authorization", other.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + id + "/sharing").header("Authorization", owner.auth()))
                .andExpect(jsonPath("$.links", hasSize(1))).andExpect(jsonPath("$.links[0].token").doesNotExist())
                .andExpect(jsonPath("$.links[0].lastUsedAt").exists());

        mvc.perform(delete("/api/v1/projects/" + id + "/sharing/links/" + linkId).header("Authorization", owner.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/shared/" + token)).andExpect(status().isNotFound());
    }

    @Test
    void redeListaBuscaFavoritaComentaEFazForkComNotificacoes() throws Exception {
        Account owner = account();
        Account fan = account();
        String name = "Clinica " + UUID.randomUUID().toString().substring(0, 6);
        String id = project(owner, name);
        publish(owner, id);

        mvc.perform(get("/api/v1/community/projects").param("q", name.toLowerCase()).header("Authorization", fan.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].author").value(owner.username()))
                .andExpect(jsonPath("$[0].license").value("MIT"));
        mvc.perform(get("/api/v1/community/projects").param("q", name).param("tech", "spring-boot").header("Authorization", fan.auth()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/community/projects").param("q", name).param("tech", "fastapi").header("Authorization", fan.auth()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/community/projects")).andExpect(status().isUnauthorized());

        mvc.perform(put("/api/v1/community/projects/" + id + "/star").header("Authorization", fan.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stars").value(1)).andExpect(jsonPath("$.starredByMe").value(true));
        mvc.perform(put("/api/v1/community/projects/" + id + "/star").header("Authorization", fan.auth())).andExpect(jsonPath("$.stars").value(1));
        mvc.perform(get("/api/v1/community/starred").header("Authorization", fan.auth())).andExpect(jsonPath("$[*].id", hasItem(id)));

        mvc.perform(post("/api/v1/community/projects/" + id + "/comments").header("Authorization", fan.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Muito bom!\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/community/projects/" + id + "/comments").header("Authorization", fan.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/community/projects/" + id + "/comments").header("Authorization", owner.auth()))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].author").value(fan.username())).andExpect(jsonPath("$[0].canDelete").value(true));

        String fork = mvc.perform(post("/api/v1/community/projects/" + id + "/fork").header("Authorization", fan.auth()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.forkedFromLabel").value("@" + owner.username() + "/" + JsonPath.read(
                        mvc.perform(get("/api/v1/projects/" + id).header("Authorization", owner.auth())).andReturn().getResponse().getContentAsString(), "$.slug")))
                .andReturn().getResponse().getContentAsString();
        String forkId = JsonPath.read(fork, "$.id");
        mvc.perform(get("/api/v1/projects/" + forkId + "/tree").header("Authorization", fan.auth())).andExpect(jsonPath("$[*].path", hasItem("backend/pom.xml")));
        mvc.perform(get("/api/v1/projects/" + forkId + "/versions").header("Authorization", fan.auth())).andExpect(jsonPath("$[0].origin").value("FORK"));
        mvc.perform(post("/api/v1/community/projects/" + id + "/fork").header("Authorization", owner.auth())).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/community/projects/" + id).header("Authorization", fan.auth())).andExpect(jsonPath("$.forks").value(1));

        // o dono recebe estrela, comentário e fork; marcar como lida zera o contador
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", owner.auth()))
                .andExpect(jsonPath("$.unread").value(3)).andExpect(jsonPath("$.items[*].kind", hasItem("STAR")))
                .andExpect(jsonPath("$.items[*].kind", hasItem("COMMENT"))).andExpect(jsonPath("$.items[*].kind", hasItem("FORK")));
        mvc.perform(post("/api/v1/me/notifications/read").header("Authorization", owner.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", owner.auth())).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", fan.auth())).andExpect(jsonPath("$.items", hasSize(0)));

        // projeto com todos os direitos reservados não permite fork; arquivado some da rede
        mvc.perform(share(owner, id, "{\"visibility\":\"COMMUNITY\",\"license\":\"ALL_RIGHTS_RESERVED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/community/projects/" + id + "/fork").header("Authorization", account().auth())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/projects/" + id + "/archive").header("Authorization", owner.auth())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/community/projects/" + id).header("Authorization", fan.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/community/projects").param("q", name).header("Authorization", fan.auth())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void denunciaChegaNaModeracaoEDespublicarAvisaODono() throws Exception {
        Account owner = account();
        Account reporter = account();
        String id = project(owner, "Agenda Denunciada");
        publish(owner, id);
        mvc.perform(post("/api/v1/community/projects/" + id + "/comments").header("Authorization", owner.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Comentário impróprio\"}")).andExpect(status().isCreated());
        int commentId = JsonPath.read(mvc.perform(get("/api/v1/community/projects/" + id + "/comments").header("Authorization", reporter.auth()))
                .andReturn().getResponse().getContentAsString(), "$[0].id");

        mvc.perform(post("/api/v1/community/projects/" + id + "/reports").header("Authorization", reporter.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OFFENSIVE\",\"commentId\":" + commentId + "}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/community/projects/" + id + "/reports").header("Authorization", reporter.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"MALICIOUS_CODE\",\"details\":\"Script suspeito\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/community/projects/" + id + "/reports").header("Authorization", reporter.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnprocessableContent());

        mvc.perform(get("/api/v1/admin/reports").header("Authorization", reporter.auth())).andExpect(status().isForbidden());
        String admin = admin();
        String open = mvc.perform(get("/api/v1/admin/reports").header("Authorization", admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> commentReports = JsonPath.read(open, "$[?(@.projectId=='" + id + "' && @.reason=='OFFENSIVE')].id");
        List<Integer> codeReports = JsonPath.read(open, "$[?(@.projectId=='" + id + "' && @.reason=='MALICIOUS_CODE')].id");
        assertThat(commentReports).hasSize(1);
        assertThat(codeReports).hasSize(1);

        mvc.perform(post("/api/v1/admin/reports/" + commentReports.getFirst() + "/resolve").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"HIDE_COMMENT\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/community/projects/" + id + "/comments").header("Authorization", reporter.auth())).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/v1/admin/reports/" + codeReports.getFirst() + "/resolve").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"UNPUBLISH\",\"note\":\"Código malicioso\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/reports/" + codeReports.getFirst() + "/resolve").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DISMISS\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/community/projects/" + id).header("Authorization", reporter.auth())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + id + "/sharing").header("Authorization", owner.auth())).andExpect(jsonPath("$.visibility").value("PRIVATE"));
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", owner.auth()))
                .andExpect(jsonPath("$.items[*].kind", hasItem("MODERATION")))
                .andExpect(jsonPath("$.items[*].message", hasItem(containsString("Código malicioso"))));
    }

    @Test
    void donoRemoveComentarioDeQuemExcluiuAConta() throws Exception {
        Account owner = account();
        Account gone = account();
        String id = project(owner, "Agenda Comentada");
        publish(owner, id);
        mvc.perform(post("/api/v1/community/projects/" + id + "/comments").header("Authorization", gone.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Vou sair da rede\"}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/me").header("Authorization", gone.auth()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Senha1234\",\"confirmation\":\"EXCLUIR\"}")).andExpect(status().is2xxSuccessful());

        String list = mvc.perform(get("/api/v1/community/projects/" + id + "/comments").header("Authorization", owner.auth()))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].author").doesNotExist()).andExpect(jsonPath("$[0].canDelete").value(true))
                .andReturn().getResponse().getContentAsString();
        int commentId = JsonPath.read(list, "$[0].id");
        mvc.perform(delete("/api/v1/community/projects/" + id + "/comments/" + commentId).header("Authorization", owner.auth())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/community/projects/" + id + "/comments").header("Authorization", owner.auth())).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/v1/community/projects/" + id + "/comments/999999").header("Authorization", owner.auth())).andExpect(status().isNotFound());
    }

    private static List<String> entries(byte[] zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        return names;
    }
}
