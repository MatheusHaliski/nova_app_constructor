package com.nova.constructor.generator;

import com.nova.constructor.generator.GenerationPlan.Artifact;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Estrutura inicial de um backend FastAPI (Python). */
@Component
public class FastApiGenerator implements StackGenerator {

    @Override
    public String id() {
        return "fastapi";
    }

    @Override
    public void generate(GenerationPlan plan, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        boolean database = spec.database() != null && spec.backend().packages().contains("sqlalchemy");
        String deps = plan.backend().dependencies().stream().map(a -> "    \"" + a.coordinate() + "\",").collect(Collectors.joining("\n"));
        String dev = plan.backend().plugins().stream().map(Artifact::coordinate).map(c -> "    \"" + c + "\",").collect(Collectors.joining("\n"));

        files.put(root + "pyproject.toml", Tpl.fill("""
                [project]
                name = "{{slug}}"
                version = "0.1.0"
                requires-python = ">={{python}}"
                dependencies = [
                {{deps}}
                ]

                [dependency-groups]
                dev = [
                {{dev}}
                ]

                [build-system]
                requires = ["hatchling"]
                build-backend = "hatchling.build"

                [tool.hatch.build.targets.wheel]
                packages = ["app"]
                """, Map.of("slug", plan.slug(), "python", spec.backend().languageVersion(), "deps", deps, "dev", dev)));
        files.put(root + "app/__init__.py", "");
        files.put(root + "app/config.py", """
                \"\"\"Configuração por variáveis de ambiente (veja .env.example).\"\"\"
                import os


                def env(name: str, default: str | None = None) -> str:
                    value = os.environ.get(name, default)
                    if value is None:
                        raise RuntimeError(f"Defina a variável de ambiente {name}.")
                    return value
                """);
        files.put(root + "app/main.py", Tpl.fill("""
                from fastapi import FastAPI

                app = FastAPI(title={{name}}, version="0.1.0")


                @app.get("/api/status")
                def status() -> dict[str, str]:
                    \"\"\"Endpoint público de verificação: confirma que a API está no ar.\"\"\"
                    return {"app": {{name}}, "status": "ok"}
                """, Map.of("name", Tpl.jsonString(spec.name()))));
        if (database) {
            files.put(root + "app/db.py", """
                    \"\"\"Conexão com o banco. A URL, o usuário e a senha vêm de variáveis de ambiente.\"\"\"
                    from sqlalchemy import URL, create_engine, make_url
                    from sqlalchemy.orm import DeclarativeBase, sessionmaker

                    from app.config import env


                    def database_url() -> URL:
                        return make_url(env("DATABASE_URL")).set(username=env("DATABASE_USER"), password=env("DATABASE_PASSWORD"))


                    class Base(DeclarativeBase):
                        pass


                    engine = create_engine(database_url(), pool_pre_ping=True)
                    SessionLocal = sessionmaker(bind=engine, autoflush=False)
                    """);
        }
        if (spec.ai() != null && "anthropic".equals(spec.ai().provider())) {
            files.put(root + "app/ai.py", Tpl.fill("""
                    \"\"\"IA externa do app. A chave vem de ANTHROPIC_API_KEY.\"\"\"
                    import anthropic

                    client = anthropic.Anthropic()


                    def ask(prompt: str) -> str:
                        response = client.messages.create(
                            model="{{model}}",
                            max_tokens=16000,
                            messages=[{"role": "user", "content": prompt}],
                        )
                        return "".join(block.text for block in response.content if block.type == "text")
                    """, Map.of("model", spec.ai().model())));
        }
        if (spec.hasFeature("tests")) {
            files.put(root + "tests/test_status.py", """
                    from fastapi.testclient import TestClient

                    from app.main import app


                    def test_status():
                        response = TestClient(app).get("/api/status")
                        assert response.status_code == 200
                        assert response.json()["status"] == "ok"
                    """);
        }
        if (spec.hasFeature("docker")) {
            files.put(root + "Dockerfile", Tpl.fill("""
                    FROM python:{{python}}-slim
                    WORKDIR /app
                    COPY pyproject.toml ./
                    COPY app ./app
                    RUN pip install --no-cache-dir .
                    EXPOSE 8080
                    CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
                    """, Map.of("python", spec.backend().languageVersion())));
        }
    }
}
