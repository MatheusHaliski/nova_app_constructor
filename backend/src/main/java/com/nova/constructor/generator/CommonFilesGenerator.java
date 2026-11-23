package com.nova.constructor.generator;

import com.nova.constructor.catalog.Catalog;
import com.nova.constructor.catalog.CatalogService;
import com.nova.constructor.generator.GenerationPlan.Artifact;
import com.nova.constructor.generator.GenerationPlan.Env;
import com.nova.constructor.generator.GenerationPlan.Side;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Arquivos da raiz do projeto: README com "como compilar", manifesto nova.json, variáveis, compose e CI. */
@Component
public class CommonFilesGenerator {

    private final CatalogService catalog;
    private final JsonMapper mapper;

    public CommonFilesGenerator(CatalogService catalog, JsonMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
    }

    public void generate(GenerationPlan plan, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        files.put("README.md", readme(plan));
        files.put("nova.json", manifest(plan));
        files.put(".gitignore", """
                .env
                .DS_Store
                node_modules/
                .next/
                dist/
                target/
                build/
                .gradle/
                __pycache__/
                .venv/
                """);
        files.put(".env.example", envExample(plan));
        if (spec.objective() != null) {
            files.put("docs/OBJETIVO.md", "# Objetivo do app\n\n" + spec.objective() + "\n");
        }
        if (spec.hasFeature("docker")) {
            files.put("docker-compose.yml", compose(plan));
        }
        if (spec.hasFeature("ci")) {
            files.put(".github/workflows/ci.yml", ci(plan));
        }
    }

    private String readme(GenerationPlan plan) {
        ProjectSpec spec = plan.spec();
        StringBuilder md = new StringBuilder("# " + spec.name() + "\n\n");
        if (spec.description() != null) {
            md.append(spec.description()).append("\n\n");
        }
        md.append("Projeto gerado pelo NOVA · Software Application Constructor. O arquivo `nova.json` guarda as escolhas do formulário.\n\n");
        md.append("## Stack\n\n| Camada | Tecnologia | Versão |\n|---|---|---|\n");
        Side b = plan.backend();
        md.append("| Backend | ").append(b.name()).append(" | ").append(b.version()).append(" |\n");
        md.append("| Linguagem | ").append(b.language()).append(" | ").append(b.languageVersion()).append(" |\n");
        md.append("| Build | ").append(b.commands().tool()).append(" | — |\n");
        if (plan.frontend() != null) {
            md.append("| Frontend | ").append(plan.frontend().name()).append(" | ").append(plan.frontend().version()).append(" |\n");
        }
        if (spec.database() != null) {
            md.append("| Banco | ").append(catalog.get().database(spec.database().id()).map(Catalog.Database::name).orElse(spec.database().id()))
                    .append(" | ").append(spec.database().version()).append(" |\n");
        }
        for (String c : spec.connectorsOrEmpty()) {
            md.append("| Conector | ").append(catalog.get().connector(c).map(Catalog.Connector::name).orElse(c)).append(" | — |\n");
        }
        if (spec.ai() != null) {
            md.append("| IA externa | ").append(catalog.get().aiProvider(spec.ai().provider()).map(Catalog.AiProvider::name).orElse(spec.ai().provider()))
                    .append(" | ").append(spec.ai().model()).append(" |\n");
        }
        md.append("\n## Como compilar e rodar\n\nCopie `.env.example` para `.env` e preencha os valores.\n\n### Backend (`backend/`)\n\n");
        boolean maven = "maven".equals(spec.backend().buildTool());
        if (maven) {
            md.append("No Linux/macOS, torne o wrapper executável uma vez: `chmod +x mvnw`.\n\n");
        }
        if ("gradle".equals(spec.backend().buildTool())) {
            md.append("Requer Gradle instalado (ou gere o wrapper com `gradle wrapper`).\n\n");
        }
        commands(md, b);
        if (plan.frontend() != null) {
            md.append("### Frontend (`frontend/`)\n\n```bash\nnpm install\n```\n\n");
            commands(md, plan.frontend());
        }
        if (spec.hasFeature("docker")) {
            md.append("### Tudo com Docker\n\n```bash\ndocker compose up --build\n```\n\n");
        }
        md.append("## Variáveis de ambiente\n\n| Variável | Usada por | Segredo |\n|---|---|---|\n");
        for (Env e : plan.env()) {
            md.append("| `").append(e.name()).append("` | ").append(e.usedBy()).append(" | ").append(e.secret() ? "sim" : "não").append(" |\n");
        }
        md.append("\n## Dependências do backend\n\n| Pacote | Escopo | Motivo |\n|---|---|---|\n");
        for (Artifact a : b.dependencies()) {
            md.append("| `").append(a.coordinate()).append("` | ").append(a.scope()).append(" | ").append(a.reason()).append(" |\n");
        }
        if (!b.plugins().isEmpty()) {
            md.append("\n## Plugins de build\n\n");
            for (Artifact a : b.plugins()) {
                md.append("- `").append(a.coordinate()).append("` — ").append(a.reason()).append("\n");
            }
        }
        return md.toString();
    }

    private static void commands(StringBuilder md, Side side) {
        md.append("Compilar:\n\n```bash\n").append(side.commands().build()).append("\n```\n\n");
        md.append("Rodar:\n\n```bash\n").append(side.commands().run()).append("\n```\n\n");
        md.append("Testar:\n\n```bash\n").append(side.commands().test()).append("\n```\n\n");
    }

    private String manifest(GenerationPlan plan) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generator", "NOVA App Constructor");
        manifest.put("catalogVersion", catalog.get().version());
        manifest.put("spec", plan.spec());
        manifest.put("backend", plan.backend());
        manifest.put("frontend", plan.frontend());
        manifest.put("env", plan.env().stream().map(Env::name).toList());
        return mapper.writeValueAsString(manifest) + "\n";
    }

    private static String envExample(GenerationPlan plan) {
        StringBuilder sb = new StringBuilder("# Copie para .env e preencha. Nunca faça commit do .env.\n");
        String group = "";
        for (Env e : plan.env()) {
            if (!e.usedBy().equals(group)) {
                group = e.usedBy();
                sb.append("\n# ").append(group).append("\n");
            }
            sb.append(e.name()).append("=").append(e.secret() ? "" : e.example()).append("\n");
        }
        return sb.toString();
    }

    private String compose(GenerationPlan plan) {
        ProjectSpec spec = plan.spec();
        StringBuilder y = new StringBuilder("name: " + plan.slug() + "\n\nservices:\n");
        String dbHost = null;
        if (spec.database() != null) {
            Catalog.Database db = catalog.get().database(spec.database().id()).orElseThrow();
            dbHost = "db";
            y.append("  db:\n    image: ").append(db.image()).append(":").append(spec.database().version()).append("\n    environment:\n");
            if ("mysql".equals(db.id())) {
                y.append("      MYSQL_DATABASE: app\n      MYSQL_USER: ${DATABASE_USER}\n      MYSQL_PASSWORD: ${DATABASE_PASSWORD}\n      MYSQL_RANDOM_ROOT_PASSWORD: \"yes\"\n");
            } else {
                y.append("      POSTGRES_DB: app\n      POSTGRES_USER: ${DATABASE_USER}\n      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}\n");
            }
            y.append("    ports: [\"").append(db.port()).append(":").append(db.port()).append("\"]\n    volumes: [db-data:")
                    .append("mysql".equals(db.id()) ? "/var/lib/mysql" : "/var/lib/postgresql/data").append("]\n\n");
        }
        for (String id : spec.connectorsOrEmpty()) {
            Catalog.Connector c = catalog.get().connector(id).orElseThrow();
            y.append("  ").append(c.id()).append(":\n    image: ").append(c.image()).append("\n");
            if ("s3".equals(c.id())) {
                y.append("    command: server /data\n    environment:\n      MINIO_ROOT_USER: ${S3_ACCESS_KEY}\n      MINIO_ROOT_PASSWORD: ${S3_SECRET_KEY}\n");
            }
            if ("opensearch".equals(c.id())) {
                y.append("    environment:\n      discovery.type: single-node\n      DISABLE_SECURITY_PLUGIN: \"true\"\n");
            }
            y.append("    ports: [\"").append(c.port()).append(":").append(c.port()).append("\"]\n\n");
        }
        y.append("  backend:\n    build: ./backend\n    env_file: .env\n");
        if (dbHost != null) {
            y.append("    environment:\n      DATABASE_URL: ").append(plan.env().getFirst().example().replace("localhost", dbHost)).append("\n    depends_on: [db]\n");
        }
        y.append("    ports: [\"8080:8080\"]\n");
        if (plan.frontend() != null) {
            y.append("\n  frontend:\n    build: ./frontend\n    environment:\n      API_URL: http://backend:8080\n    depends_on: [backend]\n    ports: [\"3000:")
                    .append("react-vite".equals(plan.frontend().id()) ? "80" : "3000").append("\"]\n");
        }
        if (dbHost != null) {
            y.append("\nvolumes:\n  db-data:\n");
        }
        return y.toString();
    }

    private static String ci(GenerationPlan plan) {
        ProjectSpec spec = plan.spec();
        StringBuilder y = new StringBuilder("name: CI\n\non:\n  push:\n  pull_request:\n\njobs:\n  backend:\n    runs-on: ubuntu-latest\n    defaults:\n      run:\n        working-directory: backend\n    steps:\n      - uses: actions/checkout@v4\n");
        switch (spec.backend().id()) {
            case "spring-boot" -> {
                y.append("      - uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: \"")
                        .append(spec.backend().languageVersion()).append("\"\n          cache: ").append(spec.backend().buildTool()).append("\n");
                if ("gradle".equals(spec.backend().buildTool())) {
                    y.append("      - uses: gradle/actions/setup-gradle@v4\n      - run: gradle check\n");
                } else {
                    y.append("      - run: chmod +x mvnw && ./mvnw -B verify\n");
                }
            }
            case "nestjs" -> y.append("      - uses: actions/setup-node@v4\n        with:\n          node-version: 22\n      - run: npm install\n      - run: npm run build\n      - run: npm test\n");
            default -> y.append("      - uses: actions/setup-python@v5\n        with:\n          python-version: \"").append(spec.backend().languageVersion())
                    .append("\"\n      - run: pip install -e . pytest httpx\n      - run: pytest\n");
        }
        if (plan.frontend() != null) {
            y.append("\n  frontend:\n    runs-on: ubuntu-latest\n    defaults:\n      run:\n        working-directory: frontend\n    steps:\n      - uses: actions/checkout@v4\n      - uses: actions/setup-node@v4\n        with:\n          node-version: 22\n      - run: npm install\n      - run: npm run build\n");
        }
        return y.toString();
    }
}
