# NOVA · Software Application Constructor

Plataforma para criar apps e web apps escolhendo backend, linguagem, frontend, banco e IA, salvar os projetos no
perfil e compartilhá-los na rede NOVA. Backlog e objetivos: board **Nova Application Constructor** no Trello.
Fases de implementação: [docs/FASES.md](docs/FASES.md).

## Arquitetura

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 (imagem na JVM 25 LTS) · Spring Boot 4.1 (Spring Framework 7, Hibernate 7, Jackson 3) · Spring Security 7 (JWT RS256 próprio) · Spring Data JPA · Flyway · MySQL 8.4/9.x |
| Frontend | Next.js 16 · React 19 · TypeScript · Tailwind CSS |
| Conectores opcionais | Redis (cotas/contadores), OpenSearch (busca), S3/MinIO (mídia), Cassandra (timeline/notificações) — desligados por padrão |

```
backend/    API Spring Boot (pacotes por funcionalidade: auth, user, privacy, admin, security)
frontend/   App Next.js (App Router, proxy.ts, route handlers como ponte autenticada)
docs/       Fases e decisões
```

## Rodar com Docker

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Frontend em http://localhost:3000, API em http://localhost:8080 (documentação em `/swagger-ui.html`).
Para subir também os conectores opcionais: `docker compose --profile conectores up --build`.

## Rodar sem Docker

Backend (precisa de um MySQL; veja as variáveis em `.env.example`):

```bash
cd backend && ./mvnw spring-boot:run
```

Para experimentar sem MySQL, com H2 em modo MySQL (só desenvolvimento):

```bash
cd backend && NOVA_ADMIN_EMAIL=admin@nova.test NOVA_ADMIN_PASSWORD=Admin12345 ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=test -Dspring-boot.run.mainClass=com.nova.constructor.TestNovaApplication
```

Frontend:

```bash
cd frontend && npm install && npm run dev
```

## IA do NOVA (Claude)

O Copilot, a ajuda por campo, a leitura de esboços, "perguntar ao projeto" e "aplicar melhoria com IA" usam o Claude.
Sem chave, o Copilot cai para regras simples e as outras funções avisam que precisam da IA.

1. Crie uma chave em console.anthropic.com (de preferência uma só para o NOVA, com limite de gasto).
2. Defina `ANTHROPIC_API_KEY` no ambiente do backend (no `.env` ou no Docker Compose). Na sessão de nuvem do Claude
   Code use `NOVA_ANTHROPIC_API_KEY`: lá `ANTHROPIC_API_KEY` não chega aos comandos, porque a própria sessão se
   autentica pela conta da Anthropic. Se as duas existirem, vale `NOVA_ANTHROPIC_API_KEY`. `NOVA_AI_MODEL` troca o
   modelo (padrão: `claude-opus-5-5`).
3. Suba o backend e confira: `GET /api/v1/copilot/status` responde `provider: "claude"`.
4. Rode a verificação real: `python3 scripts/testar_ia.py [http://localhost:8080] [esboco.png]`.

Os testes automáticos (`AiFeaturesIntegrationTest`) cobrem esse caminho com uma IA falsa, sem gastar créditos.
Quando a IA é usada, o texto da ideia, a imagem do esboço ou os arquivos envolvidos são enviados ao provedor; a tela avisa antes.

## Testes

```bash
cd backend && ./mvnw verify
```

```bash
cd frontend && npm run lint && npm run build
```
