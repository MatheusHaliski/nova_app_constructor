package com.nova.constructor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** A cota de armazenamento vale em toda gravação e conta o conteúdo guardado no histórico, não só a versão atual. */
@SpringBootTest(properties = "nova.projects.storage-limit=300KB")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorageQuotaIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void edicoesRepetidasDoMesmoArquivoEsbarramNaCotaDaConta() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String register = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Cota","username":"cota_%s","email":"cota%s@nova.test","password":"Senha1234","birthDate":"1990-01-01","acceptTerms":true}
                        """.formatted(id, id)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(register, "$.accessToken");
        String project = JsonPath.read(mvc.perform(post("/api/v1/generator/projects").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cota\",\"type\":\"APP\",\"backend\":{\"id\":\"spring-boot\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        // Cada gravação troca o conteúdo do mesmo arquivo de ~100 KB: a versão atual segue pequena, o histórico cresce.
        int version = 1;
        int accepted = 0;
        for (int i = 0; i < 6; i++) {
            String content = String.valueOf((char) ('a' + i)).repeat(100 * 1024);
            int statusCode = mvc.perform(put("/api/v1/projects/" + project + "/file").header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"path\":\"dados.txt\",\"content\":\"" + content + "\",\"baseVersion\":" + version + "}"))
                    .andReturn().getResponse().getStatus();
            if (statusCode == 200) {
                version++;
                accepted++;
            } else {
                org.assertj.core.api.Assertions.assertThat(statusCode).isEqualTo(413);
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(accepted).isBetween(1, 2);

        mvc.perform(put("/api/v1/projects/" + project + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"dados.txt\",\"content\":\"" + "z".repeat(100 * 1024) + "\",\"baseVersion\":" + version + "}"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail", containsString("Limite de armazenamento")));
        mvc.perform(get("/api/v1/projects/usage").header("Authorization", auth))
                .andExpect(jsonPath("$.storageLimitBytes").value(300 * 1024))
                .andExpect(jsonPath("$.storageBytes", org.hamcrest.Matchers.greaterThan(200 * 1024)));
        // Reverter para um conteúdo já guardado não gasta espaço novo e continua permitido.
        mvc.perform(post("/api/v1/projects/" + project + "/versions/1/restore").header("Authorization", auth)).andExpect(status().isOk());
    }
}
