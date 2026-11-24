package com.nova.constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeneratorAndProjectsIntegrationTest {

    private static final String FULL_SPEC = """
            {"name":"Loja Açaí","description":"Pedidos de açaí","objective":"Vender açaí online","type":"WEB_APP","mode":"ASSISTED",
             "backend":{"id":"spring-boot","version":"4.1.2","language":"java","languageVersion":"21","buildTool":"maven",
                        "packages":["actuator","data-jpa","flyway","jacoco","spotless","lombok"]},
             "frontend":{"id":"nextjs","packages":["tailwind","zod"]},
             "database":{"id":"mysql","version":"8.4"},
             "connectors":["redis","s3","opensearch","cassandra"],
             "ai":{"provider":"anthropic"},
             "features":["auth","openapi","docker","ci","tests"],
             "screens":["Produtos","Carrinho"]}
            """;

    private static final String MINIMAL_SPEC = """
            {"name":"API Simples","type":"APP","backend":{"id":"spring-boot"}}
            """;

    @Autowired
    MockMvc mvc;

    private String token() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String json = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Dev Teste","username":"dev_%s","email":"dev%s@nova.test","password":"Senha1234","birthDate":"1994-02-03","acceptTerms":true}
                        """.formatted(id, id)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.accessToken");
    }

    private String createProject(String auth, String spec) throws Exception {
        String json = mvc.perform(post("/api/v1/generator/projects").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(spec))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String fileContent(String auth, String id, String path) throws Exception {
        String json = mvc.perform(get("/api/v1/projects/" + id + "/file").param("path", path).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.content");
    }

    @Test
    void catalogoOfereceVersoesBuildPacotesPluginsEConectores() throws Exception {
        mvc.perform(get("/api/v1/catalog").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages[?(@.id=='java')].versions[*]", hasItem("21")))
                .andExpect(jsonPath("$.backends[?(@.id=='spring-boot')].versions[*]", hasItem("4.1.2")))
                .andExpect(jsonPath("$.backends[?(@.id=='spring-boot')].buildTools[*].id", hasItem("gradle")))
                .andExpect(jsonPath("$.backends[?(@.id=='spring-boot')].packages[?(@.kind=='plugin')].id", hasItem("jacoco")))
                .andExpect(jsonPath("$.connectors[*].id", hasItem("opensearch")))
                .andExpect(jsonPath("$.databases[?(@.id=='mysql')].versions[*]", hasItem("8.4")));
    }

    @Test
    void planoResolveDependenciasPluginsVariaveisEComandos() throws Exception {
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(FULL_SPEC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backend.commands.build").value("./mvnw clean package"))
                .andExpect(jsonPath("$.backend.dependencies[*].coordinate", hasItem("com.mysql:mysql-connector-j")))
                .andExpect(jsonPath("$.backend.dependencies[*].coordinate", hasItem("org.springframework.boot:spring-boot-starter-data-redis")))
                .andExpect(jsonPath("$.backend.dependencies[*].coordinate", hasItem("com.anthropic:anthropic-java:3.10.0")))
                .andExpect(jsonPath("$.backend.dependencies[?(@.coordinate=='org.springframework.boot:spring-boot-starter-oauth2-resource-server')].reason", hasItem("Recurso: auth")))
                .andExpect(jsonPath("$.backend.plugins[*].coordinate", hasItem("org.jacoco:jacoco-maven-plugin:0.8.16")))
                .andExpect(jsonPath("$.frontend.dependencies[*].coordinate", hasItem("next@16.4.6")))
                .andExpect(jsonPath("$.env[?(@.name=='JWT_SECRET')].secret", hasItem(true)))
                .andExpect(jsonPath("$.files", hasItem("backend/pom.xml")))
                .andExpect(jsonPath("$.files", hasItem("frontend/src/app/carrinho/page.tsx")))
                .andExpect(jsonPath("$.files", hasItem("docker-compose.yml")))
                .andExpect(jsonPath("$.files", hasItem(".github/workflows/ci.yml")));
    }

    @Test
    void planoPreencheOsPadroesDoCatalogo() throws Exception {
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(MINIMAL_SPEC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spec.backend.version").value("4.1.2"))
                .andExpect(jsonPath("$.spec.backend.languageVersion").value("21"))
                .andExpect(jsonPath("$.spec.backend.buildTool").value("maven"))
                .andExpect(jsonPath("$.spec.features", hasItem("openapi")))
                .andExpect(jsonPath("$.backend.dependencies[*].coordinate", not(hasItem("org.springframework.boot:spring-boot-starter-data-jpa"))));
    }

    @Test
    void planoRecusaEscolhasIncompativeisOuIndisponiveis() throws Exception {
        String auth = token();
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"APP\",\"backend\":{\"id\":\"nestjs\",\"language\":\"java\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("não usa a linguagem")));
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"APP\",\"backend\":{\"id\":\"django\"}}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"APP\",\"backend\":{\"id\":\"spring-boot\",\"version\":\"2.7.0\"}}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/generator/plan").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"APP\",\"backend\":{\"id\":\"spring-boot\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void geraProjetoSpringComArquivosCoerentesComAsEscolhas() throws Exception {
        String auth = token();
        String id = createProject(auth, FULL_SPEC);

        String pom = fileContent(auth, id, "backend/pom.xml");
        assertThat(pom).contains("<version>4.1.2</version>", "<java.version>21</java.version>", "spring-boot-starter-data-cassandra",
                "opensearch-java", "<artifactId>s3</artifactId>", "jacoco-maven-plugin", "spotless-maven-plugin", "<artifactId>loja-acai</artifactId>");
        String yml = fileContent(auth, id, "backend/src/main/resources/application.yml");
        assertThat(yml).contains("${DATABASE_URL}", "ddl-auto: validate", "${REDIS_URL}", "${JWT_SECRET}", "model: claude-opus-5-5");
        assertThat(yml).doesNotContain("password: nova", "sk-");
        assertThat(fileContent(auth, id, "backend/src/main/java/com/nova/lojaacai/LojaAcaiApplication.java")).contains("package com.nova.lojaacai;");
        assertThat(fileContent(auth, id, "backend/src/main/java/com/nova/lojaacai/security/SecurityConfig.java")).contains("JwtDecoder");
        assertThat(fileContent(auth, id, ".env.example")).contains("JWT_SECRET=\n", "REDIS_URL=redis://localhost:6379", "ANTHROPIC_API_KEY=\n");
        assertThat(fileContent(auth, id, "README.md")).contains("./mvnw clean package", "| Backend | Spring Boot | 4.1.2 |", "Redis");
        assertThat(fileContent(auth, id, "nova.json")).contains("\"buildTool\" : \"maven\"");
        assertThat(fileContent(auth, id, "frontend/package.json")).contains("\"next\": \"16.4.6\"", "\"zod\"");
        assertThat(fileContent(auth, id, "docker-compose.yml")).contains("image: mysql:8.4", "minio");

        mvc.perform(get("/api/v1/projects/" + id).header("Authorization", auth))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.spec.backend.id").value("spring-boot"));
    }

    @Test
    void geraVariantesGradleNestFastApiEVite() throws Exception {
        String auth = token();
        String gradle = createProject(auth, "{\"name\":\"Api Gradle\",\"type\":\"APP\",\"backend\":{\"id\":\"spring-boot\",\"buildTool\":\"gradle\",\"languageVersion\":\"25\"},\"database\":{\"id\":\"postgresql\"}}");
        assertThat(fileContent(auth, gradle, "backend/build.gradle.kts"))
                .contains("id(\"org.springframework.boot\") version \"4.1.2\"", "JavaLanguageVersion.of(25)", "runtimeOnly(\"org.postgresql:postgresql\")");

        String nest = createProject(auth, "{\"name\":\"Api Nest\",\"type\":\"WEB_APP\",\"backend\":{\"id\":\"nestjs\"},\"frontend\":{\"id\":\"react-vite\"},\"database\":{\"id\":\"postgresql\"},\"connectors\":[\"redis\"]}");
        assertThat(fileContent(auth, nest, "backend/package.json")).contains("\"@nestjs/core\": \"^12.5.1\"", "\"pg\"", "\"ioredis\"");
        assertThat(fileContent(auth, nest, "backend/src/app.module.ts")).contains("TypeOrmModule.forRoot", "type: 'postgres'");
        assertThat(fileContent(auth, nest, "frontend/vite.config.ts")).contains("defineConfig");

        String fast = createProject(auth, "{\"name\":\"Api Python\",\"type\":\"APP\",\"backend\":{\"id\":\"fastapi\",\"languageVersion\":\"3.12\"},\"database\":{\"id\":\"mysql\"},\"ai\":{\"provider\":\"anthropic\",\"model\":\"claude-haiku-5-5\"}}");
        assertThat(fileContent(auth, fast, "backend/pyproject.toml")).contains("requires-python = \">=3.12\"", "pymysql", "anthropic>=2.10.0");
        assertThat(fileContent(auth, fast, "backend/app/ai.py")).contains("claude-haiku-5-5");
    }

    @Test
    void projetoDeOutraPessoaNaoExiste() throws Exception {
        String id = createProject(token(), MINIMAL_SPEC);
        String other = token();
        mvc.perform(get("/api/v1/projects/" + id).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/" + id + "/download").header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void edicaoCriaVersoesDetectaConflitoESegredos() throws Exception {
        String auth = token();
        String id = createProject(auth, MINIMAL_SPEC);
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"backend/notas.txt\",\"content\":\"password = SuperSecreta123\",\"baseVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.secretWarnings[0].kind").value("Senha ou segredo em texto"));
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"backend/outro.txt\",\"content\":\"x\",\"baseVersion\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"../fora.txt\",\"content\":\"x\",\"baseVersion\":2}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/projects/" + id + "/files/move").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"backend/notas.txt\",\"to\":\"docs/notas.txt\",\"baseVersion\":2}"))
                .andExpect(jsonPath("$.version").value(3));
        mvc.perform(post("/api/v1/projects/" + id + "/files/delete").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"docs\",\"baseVersion\":3}"))
                .andExpect(jsonPath("$.version").value(4));
        mvc.perform(post("/api/v1/projects/" + id + "/versions/2/restore").header("Authorization", auth))
                .andExpect(jsonPath("$.version").value(5));
        assertThat(fileContent(auth, id, "backend/notas.txt")).contains("SuperSecreta123");
        mvc.perform(get("/api/v1/projects/" + id + "/versions").header("Authorization", auth))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].origin").value("RESTORE"))
                .andExpect(jsonPath("$[4].origin").value("GENERATOR"));
        mvc.perform(get("/api/v1/projects/" + id + "/tree").param("version", "4").header("Authorization", auth))
                .andExpect(jsonPath("$[*].path", not(hasItem("docs/notas.txt"))));
    }

    @Test
    void novoArquivoNaoSobrescreveExistenteECaminhoLongoNaoQuebraORotulo() throws Exception {
        String auth = token();
        String id = createProject(auth, MINIMAL_SPEC);
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"README.md\",\"content\":\"\",\"baseVersion\":1,\"createOnly\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Já existe um arquivo")));
        assertThat(fileContent(auth, id, "README.md")).contains("API Simples");

        String longPath = "docs/" + "pasta-com-nome-bem-comprido/".repeat(8) + "arquivo.txt";
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + longPath + "\",\"content\":\"x\",\"baseVersion\":1,\"createOnly\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/" + id + "/files/move").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"" + longPath + "\",\"to\":\"" + longPath.replace("arquivo", "renomeado") + "\",\"baseVersion\":2}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects/" + id + "/versions").header("Authorization", auth))
                .andExpect(jsonPath("$[0].label", org.hamcrest.Matchers.hasLength(120)));
    }

    @Test
    void autenticacaoEGeradaTambemParaNestEFastApi() throws Exception {
        String auth = token();
        String nest = createProject(auth, "{\"name\":\"Nest Auth\",\"type\":\"APP\",\"backend\":{\"id\":\"nestjs\"},\"features\":[\"auth\"]}");
        assertThat(fileContent(auth, nest, "backend/src/app.module.ts")).contains("AuthModule");
        assertThat(fileContent(auth, nest, "backend/src/auth/jwt-auth.guard.ts")).contains("UnauthorizedException");
        assertThat(fileContent(auth, nest, "backend/src/auth/auth.controller.ts")).contains("@Post('token')");
        assertThat(fileContent(auth, nest, "backend/src/status.controller.ts")).contains("@Public()");
        assertThat(fileContent(auth, nest, "backend/package.json")).contains("\"@nestjs/jwt\"").doesNotContain("passport");

        String fast = createProject(auth, "{\"name\":\"Fast Auth\",\"type\":\"APP\",\"backend\":{\"id\":\"fastapi\"},\"features\":[\"auth\"]}");
        assertThat(fileContent(auth, fast, "backend/app/main.py")).contains("app.include_router(auth.router)");
        assertThat(fileContent(auth, fast, "backend/app/auth.py")).contains("def require_user", "@router.post(\"/token\")");

        String semAuth = createProject(auth, "{\"name\":\"Nest Livre\",\"type\":\"APP\",\"backend\":{\"id\":\"nestjs\"},\"features\":[]}");
        assertThat(fileContent(auth, semAuth, "backend/src/status.controller.ts")).doesNotContain("Public");
    }

    @Test
    void downloadZipNaoLevaArquivosDeSegredo() throws Exception {
        String auth = token();
        String id = createProject(auth, MINIMAL_SPEC);
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\".env\",\"content\":\"DATABASE_PASSWORD=abc12345\",\"baseVersion\":1}"))
                .andExpect(status().isOk());
        MvcResult result = mvc.perform(get("/api/v1/projects/" + id + "/download").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"api-simples-v2.zip\""))
                .andExpect(header().exists("X-Checksum-Sha256"))
                .andReturn();
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                names.add(e.getName());
            }
        }
        assertThat(names).contains("api-simples-v2/.env.example", "api-simples-v2/backend/pom.xml", "api-simples-v2/backend/mvnw")
                .doesNotContain("api-simples-v2/.env");
        mvc.perform(get("/api/v1/projects/" + id + "/download").param("path", "backend/src").param("format", "tar.gz").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/gzip"));
    }

    @Test
    void lixeiraRestauracaoEExclusaoDefinitiva() throws Exception {
        String auth = token();
        String id = createProject(auth, MINIMAL_SPEC);
        mvc.perform(post("/api/v1/projects/" + id + "/delete-permanently").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmName\":\"API Simples\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/projects/" + id).header("Authorization", auth)).andExpect(jsonPath("$.status").value("TRASHED"));
        mvc.perform(put("/api/v1/projects/" + id + "/file").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"a.txt\",\"content\":\"x\",\"baseVersion\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/projects").param("status", "TRASHED").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/projects/" + id + "/activate").header("Authorization", auth)).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(delete("/api/v1/projects/" + id).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/projects/" + id + "/delete-permanently").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmName\":\"errado\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/projects/" + id + "/delete-permanently").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmName\":\"API Simples\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/projects/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
    }

    @Test
    void copilotSemChaveUsaRegrasEDevolvePropostaValida() throws Exception {
        String auth = token();
        mvc.perform(get("/api/v1/copilot/status").header("Authorization", auth)).andExpect(jsonPath("$.provider").value("regras"));
        mvc.perform(post("/api/v1/copilot/propose").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"Uma loja online de produtos naturais com login de clientes, busca e upload de fotos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("regras"))
                .andExpect(jsonPath("$.notice").exists())
                .andExpect(jsonPath("$.spec.mode").value("COPILOT"))
                .andExpect(jsonPath("$.spec.backend.version").value("4.1.2"))
                .andExpect(jsonPath("$.spec.features", hasItem("auth")))
                .andExpect(jsonPath("$.spec.connectors", hasItem("opensearch")))
                .andExpect(jsonPath("$.spec.connectors", hasItem("s3")))
                .andExpect(jsonPath("$.spec.screens", hasItem("Carrinho")))
                .andExpect(jsonPath("$.backlog[0].story", containsString("Como usuário")));
        mvc.perform(post("/api/v1/copilot/assist").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"screens\",\"idea\":\"agenda de consultas com login\"}"))
                .andExpect(jsonPath("$.suggestion", containsString("Agenda")));
    }
}
