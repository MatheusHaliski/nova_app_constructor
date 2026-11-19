package com.nova.constructor;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndAccessIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions register(String username, String email, String birthDate, boolean acceptTerms) throws Exception {
        String body = """
                {"name":"Ana Teste","username":"%s","email":"%s","password":"Senha1234","birthDate":"%s","acceptTerms":%s}
                """.formatted(username, email, birthDate, acceptTerms);
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String registerAndGetJson(String id) throws Exception {
        return register("ana_" + id, "ana" + id + "@nova.test", "1995-05-10", true)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
    }

    private static String bearer(String json) {
        return "Bearer " + JsonPath.read(json, "$.accessToken");
    }

    @Test
    void cadastroCriaContaComPadroesMaisProtetivos() throws Exception {
        String json = registerAndGetJson(unique());
        mvc.perform(get("/api/v1/me/settings").header("Authorization", bearer(json)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacy.profileVisibility").value("PRIVATE"))
                .andExpect(jsonPath("$.privacy.consents.PERSONALIZED_FEED.granted").value(false))
                .andExpect(jsonPath("$.privacy.consents.AI_IMPROVEMENT.granted").value(false))
                .andExpect(jsonPath("$.privacy.consents.PARTNER_SHARING.granted").value(false))
                .andExpect(jsonPath("$.preferences.theme").value("SYSTEM"))
                .andExpect(jsonPath("$.terms.version").value("2026-11"));
    }

    @Test
    void cadastroExigeAceiteDosTermosEIdadeMinima() throws Exception {
        String id = unique();
        register("bia_" + id, "bia" + id + "@nova.test", "1995-05-10", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.acceptTerms").exists());
        register("bia_" + id, "bia" + id + "@nova.test", "2015-01-01", true)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void cadastroRecusaEmailDuplicado() throws Exception {
        String id = unique();
        registerAndGetJson(id);
        register("outro_" + id, "ana" + id + "@nova.test", "1990-01-01", true).andExpect(status().isConflict());
    }

    @Test
    void rotasProtegidasExigemToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
    }

    @Test
    void usuarioComumNaoAcessaAdministracao() throws Exception {
        String json = registerAndGetJson(unique());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(json)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
    }

    @Test
    void administradorListaUsuariosENaoRemoveOProprioPerfil() throws Exception {
        String admin = login("admin@nova.test", "Admin12345");
        String adminId = JsonPath.read(admin, "$.user.id");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email", hasItem("admin@nova.test")));
        mvc.perform(put("/api/v1/admin/users/" + adminId + "/roles").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void administradorPromoveUsuarioEDesativaConta() throws Exception {
        String admin = login("admin@nova.test", "Admin12345");
        String user = registerAndGetJson(unique());
        String userId = JsonPath.read(user, "$.user.id");
        mvc.perform(put("/api/v1/admin/users/" + userId + "/roles").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("ADMIN")))
                .andExpect(jsonPath("$.roles", hasItem("USER")));
        mvc.perform(put("/api/v1/admin/users/" + userId + "/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void loginBloqueiaContaAposTentativasInvalidas() throws Exception {
        String id = unique();
        registerAndGetJson(id);
        String email = "ana" + id + "@nova.test";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"errada123\"}".formatted(email)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Senha1234\"}".formatted(email)))
                .andExpect(status().isLocked());
    }

    @Test
    void refreshRotacionaEReusoRevogaAFamilia() throws Exception {
        String json = registerAndGetJson(unique());
        String first = JsonPath.read(json, "$.refreshToken");
        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(rotated, "$.refreshToken");

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(second)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consentimentoPodeSerConcedidoERevogadoEApareceNaExportacao() throws Exception {
        String auth = bearer(registerAndGetJson(unique()));
        mvc.perform(put("/api/v1/me/consents/PRODUCT_ANALYTICS").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"granted\":true}"))
                .andExpect(jsonPath("$.privacy.consents.PRODUCT_ANALYTICS.granted").value(true));
        mvc.perform(put("/api/v1/me/consents/PRODUCT_ANALYTICS").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"granted\":false}"))
                .andExpect(jsonPath("$.privacy.consents.PRODUCT_ANALYTICS.granted").value(false));
        mvc.perform(get("/api/v1/me/export").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"nova-meus-dados.json\""))
                .andExpect(jsonPath("$.consentHistory.length()").value(2))
                .andExpect(jsonPath("$.user.email").exists());
    }

    @Test
    void contaPrivacidadeEPreferenciasSaoAtualizadas() throws Exception {
        String id = unique();
        String auth = bearer(registerAndGetJson(id));
        mvc.perform(put("/api/v1/me/account").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Souza\",\"username\":\"ana_souza_%s\",\"bio\":\"BSI\",\"birthDate\":\"1995-05-10\"}".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.name").value("Ana Souza"));
        mvc.perform(put("/api/v1/me/privacy").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileVisibility\":\"COMMUNITY\"}"))
                .andExpect(jsonPath("$.privacy.profileVisibility").value("COMMUNITY"));
        mvc.perform(put("/api/v1/me/preferences").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"language\":\"en-US\"}"))
                .andExpect(jsonPath("$.preferences.theme").value("DARK"))
                .andExpect(jsonPath("$.preferences.language").value("en-US"));
        mvc.perform(put("/api/v1/me/email").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"novo%s@nova.test\",\"currentPassword\":\"errada123\"}".formatted(id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void exclusaoDeContaExigeSenhaEConfirmacao() throws Exception {
        String id = unique();
        String auth = bearer(registerAndGetJson(id));
        mvc.perform(delete("/api/v1/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha1234\",\"confirmation\":\"sim\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/me").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Senha1234\",\"confirmation\":\"EXCLUIR\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana%s@nova.test\",\"password\":\"Senha1234\"}".formatted(id)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorVeMySqlCentralEConectoresOpcionaisDesligados() throws Exception {
        String admin = login("admin@nova.test", "Admin12345");
        mvc.perform(get("/api/v1/admin/system/data-sources").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("mysql"))
                .andExpect(jsonPath("$[0].required").value(true))
                .andExpect(jsonPath("$[1].enabled").value(false))
                .andExpect(jsonPath("$[4].enabled").value(false));
    }

    @Test
    void jwksPublicaApenasAChavePublica() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }
}
