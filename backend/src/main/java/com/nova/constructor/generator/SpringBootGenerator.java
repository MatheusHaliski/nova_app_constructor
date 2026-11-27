package com.nova.constructor.generator;

import com.nova.constructor.generator.GenerationPlan.Artifact;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Estrutura inicial de um backend Spring Boot: build (Maven ou Gradle), aplicação, configuração por variáveis de
 * ambiente, migração inicial, e o código de cada recurso escolhido (autenticação, conectores, IA externa).
 */
@Component
public class SpringBootGenerator implements StackGenerator {

    @Override
    public String id() {
        return "spring-boot";
    }

    @Override
    public void generate(GenerationPlan plan, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        String pkg = "com.nova." + plan.slug().replace("-", "");
        String pkgPath = root + "src/main/java/" + pkg.replace('.', '/') + "/";
        String testPath = root + "src/test/java/" + pkg.replace('.', '/') + "/";
        String appClass = Tpl.pascal(plan.slug()) + "Application";
        Map<String, String> v = Map.of("pkg", pkg, "app", appClass, "name", spec.name(), "slug", plan.slug());

        if ("gradle".equals(spec.backend().buildTool())) {
            files.put(root + "settings.gradle.kts", "rootProject.name = \"" + plan.slug() + "\"\n");
            files.put(root + "build.gradle.kts", gradle(plan));
        } else {
            files.put(root + "pom.xml", pom(plan, pkg));
            files.put(root + "mvnw", Tpl.resource("templates/maven/mvnw"));
            files.put(root + "mvnw.cmd", Tpl.resource("templates/maven/mvnw.cmd"));
            files.put(root + ".mvn/wrapper/maven-wrapper.properties", Tpl.resource("templates/maven/.mvn/wrapper/maven-wrapper.properties"));
        }

        files.put(pkgPath + appClass + ".java", Tpl.fill("""
                package {{pkg}};

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class {{app}} {

                    public static void main(String[] args) {
                        SpringApplication.run({{app}}.class, args);
                    }
                }
                """, v));

        files.put(pkgPath + "web/StatusController.java", Tpl.fill("""
                package {{pkg}}.web;

                import java.util.Map;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                /** Endpoint público de verificação: confirma que a API está no ar. */
                @RestController
                @RequestMapping("/api/status")
                public class StatusController {

                    @GetMapping
                    public Map<String, String> status() {
                        return Map.of("app", "{{name}}", "status", "ok");
                    }
                }
                """, v));

        files.put(root + "src/main/resources/application.yml", applicationYml(plan));

        if (spec.database() != null && spec.backend().packages().contains("flyway")) {
            files.put(root + "src/main/resources/db/migration/V1__init.sql", """
                    -- Migração inicial. Crie novas migrações (V2__..., V3__...) em vez de alterar esta.
                    CREATE TABLE app_setting (
                        name       VARCHAR(100) NOT NULL,
                        content    VARCHAR(500) NOT NULL,
                        updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT pk_app_setting PRIMARY KEY (name)
                    );
                    """);
        }

        if (spec.hasFeature("auth")) {
            files.put(pkgPath + "security/SecurityConfig.java", Tpl.fill(SECURITY_CONFIG, v));
            files.put(pkgPath + "security/AuthController.java", Tpl.fill(AUTH_CONTROLLER, v));
        }
        if (spec.connectorsOrEmpty().contains("s3")) {
            files.put(pkgPath + "connector/S3Config.java", Tpl.fill(S3_CONFIG, v));
        }
        if (spec.connectorsOrEmpty().contains("opensearch")) {
            files.put(pkgPath + "connector/OpenSearchConfig.java", Tpl.fill(OPENSEARCH_CONFIG, v));
        }
        if (spec.ai() != null && "anthropic".equals(spec.ai().provider())) {
            files.put(pkgPath + "ai/AiService.java", Tpl.fill(AI_SERVICE, v));
        }
        if (spec.hasFeature("tests")) {
            files.put(testPath + "web/StatusControllerTest.java", Tpl.fill("""
                    package {{pkg}}.web;

                    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
                    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
                    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

                    import org.junit.jupiter.api.Test;
                    import org.springframework.test.web.servlet.MockMvc;
                    import org.springframework.test.web.servlet.setup.MockMvcBuilders;

                    class StatusControllerTest {

                        private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StatusController()).build();

                        @Test
                        void statusRespondeOk() throws Exception {
                            mvc.perform(get("/api/status")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
                        }
                    }
                    """, v));
        }
        DomainCode.spring(plan, pkg, pkgPath, testPath, root, files);
        if (spec.hasFeature("docker")) {
            boolean gradle = "gradle".equals(spec.backend().buildTool());
            String jdk = spec.backend().languageVersion();
            files.put(root + "Dockerfile", gradle ? Tpl.fill("""
                    FROM gradle:jdk{{jdk}} AS build
                    WORKDIR /app
                    COPY . .
                    RUN gradle bootJar --no-daemon && cp build/libs/*.jar app.jar

                    FROM eclipse-temurin:{{jdk}}-jre
                    WORKDIR /app
                    COPY --from=build /app/app.jar app.jar
                    EXPOSE 8080
                    ENTRYPOINT ["java", "-jar", "app.jar"]
                    """, Map.of("jdk", jdk)) : Tpl.fill("""
                    FROM maven:3.9-eclipse-temurin-{{jdk}} AS build
                    WORKDIR /app
                    COPY pom.xml .
                    RUN mvn -q -B dependency:go-offline
                    COPY src ./src
                    RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

                    FROM eclipse-temurin:{{jdk}}-jre
                    WORKDIR /app
                    COPY --from=build /app/app.jar app.jar
                    EXPOSE 8080
                    ENTRYPOINT ["java", "-jar", "app.jar"]
                    """, Map.of("jdk", jdk)));
        }
    }

    // ---------------- build ----------------

    private record Gav(String group, String artifact, String version) {
        static Gav parse(String coordinate) {
            String[] p = coordinate.split(":");
            return new Gav(p[0], p[1], p.length > 2 ? p[2] : null);
        }
    }

    private String pom(GenerationPlan plan, String pkg) {
        ProjectSpec spec = plan.spec();
        StringBuilder deps = new StringBuilder();
        for (Artifact a : plan.backend().dependencies()) {
            Gav g = Gav.parse(a.coordinate());
            deps.append("        <dependency>\n")
                    .append("            <groupId>").append(g.group()).append("</groupId>\n")
                    .append("            <artifactId>").append(g.artifact()).append("</artifactId>\n");
            if (g.version() != null) {
                deps.append("            <version>").append(g.version()).append("</version>\n");
            }
            if (!"compile".equals(a.scope())) {
                deps.append("            <scope>").append(a.scope()).append("</scope>\n");
            }
            if ("provided".equals(a.scope())) {
                deps.append("            <optional>true</optional>\n");
            }
            deps.append("        </dependency>\n");
        }
        StringBuilder plugins = new StringBuilder();
        for (Artifact a : plan.backend().plugins()) {
            Gav g = Gav.parse(a.coordinate());
            plugins.append("            <plugin>\n")
                    .append("                <groupId>").append(g.group()).append("</groupId>\n")
                    .append("                <artifactId>").append(g.artifact()).append("</artifactId>\n");
            if (g.version() != null) {
                plugins.append("                <version>").append(g.version()).append("</version>\n");
            }
            if (g.artifact().equals("jacoco-maven-plugin")) {
                plugins.append("""
                                        <executions>
                                            <execution>
                                                <goals><goal>prepare-agent</goal></goals>
                                            </execution>
                                            <execution>
                                                <id>report</id>
                                                <phase>verify</phase>
                                                <goals><goal>report</goal></goals>
                                            </execution>
                                        </executions>
                        """);
            }
            if (g.artifact().equals("spotless-maven-plugin")) {
                plugins.append("""
                                        <configuration>
                                            <java>
                                                <googleJavaFormat/>
                                            </java>
                                        </configuration>
                        """);
            }
            plugins.append("            </plugin>\n");
        }
        return Tpl.fill("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>{{boot}}</version>
                        <relativePath/>
                    </parent>

                    <groupId>{{group}}</groupId>
                    <artifactId>{{slug}}</artifactId>
                    <version>0.1.0-SNAPSHOT</version>
                    <name>{{name}}</name>

                    <properties>
                        <java.version>{{java}}</java.version>
                    </properties>

                    <dependencies>
                {{deps}}    </dependencies>

                    <build>
                        <plugins>
                {{plugins}}        </plugins>
                    </build>
                </project>
                """, Map.of("boot", spec.backend().version(), "group", pkg.substring(0, pkg.lastIndexOf('.')), "slug", plan.slug(),
                "name", xml(spec.name()), "java", spec.backend().languageVersion(), "deps", deps.toString(), "plugins", plugins.toString()));
    }

    private String gradle(GenerationPlan plan) {
        ProjectSpec spec = plan.spec();
        boolean jacoco = plan.backend().plugins().stream().anyMatch(p -> p.coordinate().contains("jacoco"));
        StringBuilder deps = new StringBuilder();
        for (Artifact a : plan.backend().dependencies()) {
            String conf = switch (a.scope()) {
                case "runtime" -> "runtimeOnly";
                case "test" -> "testImplementation";
                case "provided" -> "compileOnly";
                default -> "implementation";
            };
            deps.append("    ").append(conf).append("(\"").append(a.coordinate()).append("\")\n");
            if (a.coordinate().startsWith("org.projectlombok:lombok")) {
                deps.append("    annotationProcessor(\"").append(a.coordinate()).append("\")\n");
            }
        }
        return Tpl.fill("""
                import org.springframework.boot.gradle.plugin.SpringBootPlugin

                plugins {
                    java
                    id("org.springframework.boot") version "{{boot}}"
                {{jacoco}}}

                group = "com.nova"
                version = "0.1.0-SNAPSHOT"

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of({{java}})
                    }
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
                    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
                {{deps}}    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.withType<Test> {
                    useJUnitPlatform()
                }
                """, Map.of("boot", spec.backend().version(), "java", spec.backend().languageVersion(),
                "jacoco", jacoco ? "    jacoco\n" : "", "deps", deps.toString()));
    }

    // ---------------- configuração ----------------

    private String applicationYml(GenerationPlan plan) {
        ProjectSpec spec = plan.spec();
        List<String> packages = spec.backend().packages();
        StringBuilder y = new StringBuilder();
        y.append("# Valores sensíveis vêm de variáveis de ambiente (veja .env.example); nada de segredo neste arquivo.\n");
        y.append("spring:\n  application:\n    name: ").append(plan.slug()).append("\n");
        // Erros de validação voltam como ProblemDetail (RFC 9457), com a mensagem escrita no código.
        y.append("  mvc:\n    problemdetails:\n      enabled: true\n");
        if (spec.database() != null) {
            y.append("  datasource:\n    url: ${DATABASE_URL}\n    username: ${DATABASE_USER}\n    password: ${DATABASE_PASSWORD}\n");
            if (packages.contains("data-jpa")) {
                y.append("  jpa:\n    open-in-view: false\n    hibernate:\n      ddl-auto: ")
                        .append(packages.contains("flyway") ? "validate" : "update").append("\n");
            }
        }
        if (spec.connectorsOrEmpty().contains("redis")) {
            y.append("  data:\n    redis:\n      url: ${REDIS_URL}\n");
        }
        if (spec.connectorsOrEmpty().contains("cassandra")) {
            y.append("  cassandra:\n    contact-points: ${CASSANDRA_CONTACT_POINTS}\n    local-datacenter: datacenter1\n");
        }
        y.append("\nserver:\n  port: ${PORT:8080}\n");
        if (packages.contains("actuator")) {
            y.append("\nmanagement:\n  endpoints:\n    web:\n      exposure:\n        include: health,info\n");
        }
        StringBuilder app = new StringBuilder();
        if (spec.hasFeature("auth")) {
            app.append("  jwt:\n    secret: ${JWT_SECRET}\n    ttl: 30m\n  admin:\n    username: ${APP_ADMIN_USER}\n    password: ${APP_ADMIN_PASSWORD}\n");
        }
        if (spec.connectorsOrEmpty().contains("s3")) {
            app.append("  s3:\n    endpoint: ${S3_ENDPOINT}\n    bucket: ${S3_BUCKET}\n    access-key: ${S3_ACCESS_KEY}\n    secret-key: ${S3_SECRET_KEY}\n");
        }
        if (spec.connectorsOrEmpty().contains("opensearch")) {
            app.append("  opensearch:\n    url: ${OPENSEARCH_URL}\n");
        }
        if (spec.ai() != null) {
            app.append("  ai:\n    api-key: ${ANTHROPIC_API_KEY}\n    model: ").append(spec.ai().model()).append("\n");
        }
        if (!app.isEmpty()) {
            y.append("\napp:\n").append(app);
        }
        return y.toString();
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------------- modelos de código ----------------

    private static final String SECURITY_CONFIG = """
            package {{pkg}}.security;

            import com.nimbusds.jose.jwk.source.ImmutableSecret;
            import java.nio.charset.StandardCharsets;
            import javax.crypto.SecretKey;
            import javax.crypto.spec.SecretKeySpec;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.security.config.annotation.web.builders.HttpSecurity;
            import org.springframework.security.config.http.SessionCreationPolicy;
            import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
            import org.springframework.security.oauth2.jwt.JwtDecoder;
            import org.springframework.security.oauth2.jwt.JwtEncoder;
            import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
            import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
            import org.springframework.security.web.SecurityFilterChain;

            /** API sem estado, protegida por token JWT. Rotas públicas: status, login, health, documentação e a página de erro (para 404 e 400 chegarem a quem chamou). */
            @Configuration
            public class SecurityConfig {

                @Bean
                SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                    http.csrf(csrf -> csrf.disable())
                            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                            .authorizeHttpRequests(auth -> auth
                                    .requestMatchers("/error", "/api/status", "/api/auth/**", "/actuator/health/**",
                                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                                    .anyRequest().authenticated())
                            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}));
                    return http.build();
                }

                /** Chave de assinatura vinda de JWT_SECRET (mínimo de 32 caracteres). */
                @Bean
                SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
                    if (secret.length() < 32) {
                        throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres.");
                    }
                    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                }

                @Bean
                JwtDecoder jwtDecoder(SecretKey jwtKey) {
                    return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
                }

                @Bean
                JwtEncoder jwtEncoder(SecretKey jwtKey) {
                    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
                }
            }
            """;

    private static final String AUTH_CONTROLLER = """
            package {{pkg}}.security;

            import java.security.MessageDigest;
            import java.nio.charset.StandardCharsets;
            import java.time.Duration;
            import java.time.Instant;
            import java.util.Map;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.http.HttpStatus;
            import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
            import org.springframework.security.oauth2.jwt.JwsHeader;
            import org.springframework.security.oauth2.jwt.JwtClaimsSet;
            import org.springframework.security.oauth2.jwt.JwtEncoder;
            import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.server.ResponseStatusException;

            /**
             * Login inicial com o usuário administrador das variáveis APP_ADMIN_USER e APP_ADMIN_PASSWORD.
             * Ponto de partida: troque por cadastro de usuários no banco quando o app evoluir.
             */
            @RestController
            @RequestMapping("/api/auth")
            public class AuthController {

                public record LoginRequest(String username, String password) {}

                private final JwtEncoder encoder;
                private final String adminUser;
                private final String adminPassword;
                private final Duration ttl;

                public AuthController(JwtEncoder encoder,
                                      @Value("${app.admin.username}") String adminUser,
                                      @Value("${app.admin.password}") String adminPassword,
                                      @Value("${app.jwt.ttl}") Duration ttl) {
                    this.encoder = encoder;
                    this.adminUser = adminUser;
                    this.adminPassword = adminPassword;
                    this.ttl = ttl;
                }

                @PostMapping("/token")
                public Map<String, Object> token(@RequestBody LoginRequest request) {
                    if (request.username() == null || request.password() == null
                            || !same(request.username(), adminUser) || !same(request.password(), adminPassword)) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos.");
                    }
                    Instant now = Instant.now();
                    JwtClaimsSet claims = JwtClaimsSet.builder()
                            .subject(request.username())
                            .issuedAt(now)
                            .expiresAt(now.plus(ttl))
                            .claim("scope", "admin")
                            .build();
                    String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
                    return Map.of("accessToken", token, "tokenType", "Bearer", "expiresIn", ttl.toSeconds());
                }

                /** Comparação em tempo constante. */
                private static boolean same(String a, String b) {
                    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
                }
            }
            """;

    private static final String S3_CONFIG = """
            package {{pkg}}.connector;

            import java.net.URI;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Lazy;
            import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
            import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
            import software.amazon.awssdk.regions.Region;
            import software.amazon.awssdk.services.s3.S3Client;

            /** Conector opcional S3/MinIO para arquivos e mídia. */
            @Configuration
            public class S3Config {

                @Bean
                @Lazy
                S3Client s3Client(@Value("${app.s3.endpoint}") String endpoint,
                                  @Value("${app.s3.access-key}") String accessKey,
                                  @Value("${app.s3.secret-key}") String secretKey) {
                    return S3Client.builder()
                            .endpointOverride(URI.create(endpoint))
                            .region(Region.US_EAST_1)
                            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                            .forcePathStyle(true)
                            .build();
                }
            }
            """;

    private static final String OPENSEARCH_CONFIG = """
            package {{pkg}}.connector;

            import java.net.URISyntaxException;
            import org.apache.hc.core5.http.HttpHost;
            import org.opensearch.client.opensearch.OpenSearchClient;
            import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Lazy;

            /** Conector opcional OpenSearch para busca textual. */
            @Configuration
            public class OpenSearchConfig {

                @Bean
                @Lazy
                OpenSearchClient openSearchClient(@Value("${app.opensearch.url}") String url) throws URISyntaxException {
                    return new OpenSearchClient(ApacheHttpClient5TransportBuilder.builder(HttpHost.create(url)).build());
                }
            }
            """;

    private static final String AI_SERVICE = """
            package {{pkg}}.ai;

            import com.anthropic.client.AnthropicClient;
            import com.anthropic.client.okhttp.AnthropicOkHttpClient;
            import com.anthropic.models.messages.Message;
            import com.anthropic.models.messages.MessageCreateParams;
            import java.util.stream.Collectors;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Service;

            /** IA externa do app: envia um texto ao Claude e devolve a resposta. A chave vem de ANTHROPIC_API_KEY. */
            @Service
            public class AiService {

                private final AnthropicClient client;
                private final String model;

                public AiService(@Value("${app.ai.api-key}") String apiKey, @Value("${app.ai.model}") String model) {
                    this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                    this.model = model;
                }

                public String ask(String prompt) {
                    Message response = client.messages().create(MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(16000L)
                            .addUserMessage(prompt)
                            .build());
                    return response.content().stream()
                            .flatMap(block -> block.text().stream())
                            .map(text -> text.text())
                            .collect(Collectors.joining());
                }
            }
            """;
}
