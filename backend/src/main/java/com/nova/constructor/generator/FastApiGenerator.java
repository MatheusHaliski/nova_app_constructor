package com.nova.constructor.generator;

import com.nova.constructor.generator.GenerationPlan.Artifact;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Estrutura inicial de um backend FastAPI (Python). */
@Component
public class FastApiGenerator implements StackGenerator {

    private static final String AUTH = """
            \"\"\"Autenticação por token JWT.

            Login inicial com o usuário administrador das variáveis APP_ADMIN_USER e APP_ADMIN_PASSWORD.
            Para proteger uma rota, use ``Depends(require_user)``.
            \"\"\"
            import hmac
            from datetime import datetime, timedelta, timezone

            import jwt
            from fastapi import APIRouter, Depends, HTTPException, status
            from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
            from pydantic import BaseModel

            from app.config import env

            TTL = timedelta(minutes=30)
            router = APIRouter(prefix="/api/auth", tags=["auth"])
            bearer = HTTPBearer(auto_error=False)


            def secret() -> str:
                value = env("JWT_SECRET")
                if len(value) < 32:
                    raise RuntimeError("JWT_SECRET precisa ter pelo menos 32 caracteres.")
                return value


            class LoginRequest(BaseModel):
                username: str
                password: str


            @router.post("/token")
            def token(body: LoginRequest) -> dict[str, str | int]:
                valid = hmac.compare_digest(body.username.encode(), env("APP_ADMIN_USER").encode()) and hmac.compare_digest(
                    body.password.encode(), env("APP_ADMIN_PASSWORD").encode()
                )
                if not valid:
                    raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Usuário ou senha inválidos.")
                now = datetime.now(timezone.utc)
                claims = {"sub": body.username, "scope": "admin", "iat": now, "exp": now + TTL}
                return {
                    "accessToken": jwt.encode(claims, secret(), algorithm="HS256"),
                    "tokenType": "Bearer",
                    "expiresIn": int(TTL.total_seconds()),
                }


            def require_user(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> dict:
                \"\"\"Dependência para rotas protegidas: valida o token e devolve as claims.\"\"\"
                if credentials is None:
                    raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Faça login para continuar.")
                try:
                    return jwt.decode(credentials.credentials, secret(), algorithms=["HS256"])
                except jwt.PyJWTError as error:
                    raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token inválido ou expirado.") from error


            @router.get("/me")
            def me(user: dict = Depends(require_user)) -> dict:
                \"\"\"Exemplo de rota protegida: devolve os dados do token de quem chamou.\"\"\"
                return user
            """;

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
        boolean auth = spec.hasFeature("auth");
        if (auth) {
            files.put(root + "app/auth.py", AUTH);
        }
        files.put(root + "app/main.py", Tpl.fill("""
                from fastapi import FastAPI
                {{authImport}}
                app = FastAPI(title={{name}}, version="0.1.0")
                {{authRouter}}

                @app.get("/api/status")
                def status() -> dict[str, str]:
                    \"\"\"Endpoint público de verificação: confirma que a API está no ar.\"\"\"
                    return {"app": {{name}}, "status": "ok"}
                """, Map.of("name", Tpl.jsonString(spec.name()),
                "authImport", auth ? "\nfrom app import auth\n" : "",
                "authRouter", auth ? "app.include_router(auth.router)\n" : "")));
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
