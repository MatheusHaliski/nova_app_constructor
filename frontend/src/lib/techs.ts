import "server-only";
import * as si from "simple-icons";

/**
 * Catálogo de tecnologias que o NOVA integra (acervo — RF-4.04 e RF-4.25). Os ícones vêm do Simple Icons
 * (CC0); marcas sem ícone livre usam o monograma no estilo NOVA. Os usos são nominativos.
 */

export type TechCategory =
  | "linguagem"
  | "backend"
  | "frontend"
  | "mobile"
  | "banco"
  | "conector"
  | "api"
  | "ia"
  | "infra"
  | "qualidade";

export type Tech = {
  id: string;
  name: string;
  category: TechCategory;
  /** Papel no NOVA: stack do próprio NOVA, opção para os apps gerados, ou conector opcional. */
  role: "nova" | "opcao" | "conector";
  description: string;
  icon?: { path: string; hex: string };
};

type SimpleIcon = { slug: string; path: string; hex: string };
const bySlug = new Map<string, SimpleIcon>(
  Object.values(si as unknown as Record<string, SimpleIcon>)
    .filter((x): x is SimpleIcon => Boolean(x && typeof x === "object" && "slug" in x))
    .map((x) => [x.slug, x]),
);

function icon(slug?: string) {
  const found = slug ? bySlug.get(slug) : undefined;
  return found ? { path: found.path, hex: `#${found.hex}` } : undefined;
}

function t(id: string, name: string, category: TechCategory, role: Tech["role"], description: string, slug?: string): Tech {
  return { id, name, category, role, description, icon: icon(slug ?? id) };
}

export const TECHS: Tech[] = [
  t("openjdk", "Java 21", "linguagem", "nova", "Linguagem do backend do NOVA; imagem roda na JVM 25 LTS."),
  t("springboot", "Spring Boot 4.1", "backend", "nova", "Framework do backend: Spring Framework 7, Hibernate 7, Jackson 3."),
  t("springsecurity", "Spring Security 7", "backend", "nova", "Autenticação JWT RS256 própria e controle de acesso por perfil."),
  t("hibernate", "Spring Data JPA / Hibernate", "backend", "nova", "Persistência no MySQL com validação do esquema."),
  t("flyway", "Flyway", "banco", "nova", "Migrações versionadas do banco."),
  t("mysql", "MySQL 8.4 / 9.x", "banco", "nova", "Fonte central de dados do NOVA."),
  t("nextdotjs", "Next.js 16", "frontend", "nova", "Frontend do NOVA com App Router."),
  t("react", "React 19", "frontend", "nova", "Biblioteca de interface."),
  t("typescript", "TypeScript", "linguagem", "nova", "Tipagem estática no frontend."),
  t("tailwindcss", "Tailwind CSS", "frontend", "nova", "Estilos utilitários com tokens de tema."),
  t("threedotjs", "Three.js / React Three Fiber", "frontend", "nova", "Cenas 3D (arquitetura e capas de projeto)."),
  t("redis", "Redis", "conector", "conector", "Opcional: cotas, contadores e limites de requisição."),
  t("opensearch", "OpenSearch", "conector", "conector", "Opcional: busca de projetos e tecnologias."),
  t("s3", "S3 / MinIO", "conector", "conector", "Opcional: mídia e pacotes gerados.", "minio"),
  t("apachecassandra", "Cassandra", "conector", "conector", "Opcional: timeline e notificações com TTL."),
  t("docker", "Docker", "infra", "nova", "Imagens e docker-compose de desenvolvimento."),
  t("nodedotjs", "Node.js", "backend", "opcao", "Runtime JavaScript para backends gerados."),
  t("nestjs", "NestJS", "backend", "opcao", "Backend modular em TypeScript."),
  t("dotnet", ".NET", "backend", "opcao", "Web API ou Minimal APIs em C#."),
  t("django", "Django", "backend", "opcao", "Backend Python com apps e ORM."),
  t("fastapi", "FastAPI", "backend", "opcao", "APIs Python assíncronas."),
  t("laravel", "Laravel", "backend", "opcao", "Backend PHP MVC."),
  t("rubyonrails", "Ruby on Rails", "backend", "opcao", "Backend Ruby MVC."),
  t("go", "Go", "linguagem", "opcao", "Serviços compilados e enxutos."),
  t("python", "Python", "linguagem", "opcao", "Linguagem para Django e FastAPI."),
  t("kotlin", "Kotlin", "linguagem", "opcao", "JVM e Android."),
  t("php", "PHP", "linguagem", "opcao", "Linguagem do Laravel."),
  t("angular", "Angular", "frontend", "opcao", "Frontend com componentes standalone."),
  t("vuedotjs", "Vue", "frontend", "opcao", "Frontend progressivo."),
  t("nuxt", "Nuxt", "frontend", "opcao", "Vue com SSR e layers."),
  t("svelte", "Svelte", "frontend", "opcao", "Frontend compilado."),
  t("astro", "Astro", "frontend", "opcao", "Sites de conteúdo com ilhas."),
  t("vite", "Vite", "frontend", "opcao", "SPA com build rápido."),
  t("flutter", "Flutter", "mobile", "opcao", "Apps móveis multiplataforma."),
  t("expo", "React Native / Expo", "mobile", "opcao", "Apps móveis em React."),
  t("android", "Android nativo", "mobile", "opcao", "Kotlin + Jetpack."),
  t("swift", "iOS / Swift", "mobile", "opcao", "SwiftUI."),
  t("postgresql", "PostgreSQL", "banco", "opcao", "Banco relacional."),
  t("mariadb", "MariaDB", "banco", "opcao", "Banco compatível com MySQL."),
  t("mongodb", "MongoDB", "banco", "opcao", "Banco de documentos."),
  t("sqlite", "SQLite", "banco", "opcao", "Banco embarcado."),
  t("graphql", "GraphQL", "api", "opcao", "API com schema tipado."),
  t("trpc", "tRPC", "api", "opcao", "Tipos TypeScript ponta a ponta."),
  t("openapiinitiative", "OpenAPI / REST", "api", "nova", "Contrato REST documentado."),
  t("apachekafka", "Kafka", "api", "opcao", "Eventos assíncronos."),
  t("rabbitmq", "RabbitMQ", "api", "opcao", "Mensageria."),
  t("anthropic", "Claude (Anthropic)", "ia", "opcao", "IA externa para os apps gerados."),
  t("googlegemini", "Gemini", "ia", "opcao", "IA externa para os apps gerados."),
  t("openai", "OpenAI", "ia", "opcao", "IA externa para os apps gerados."),
  t("mistralai", "Mistral", "ia", "opcao", "IA externa para os apps gerados."),
  t("ollama", "Ollama", "ia", "opcao", "Modelos locais."),
  t("kubernetes", "Kubernetes", "infra", "opcao", "Orquestração de contêineres."),
  t("githubactions", "GitHub Actions", "infra", "opcao", "Integração contínua."),
  t("junit5", "JUnit 5", "qualidade", "nova", "Testes do backend."),
  t("vitest", "Vitest", "qualidade", "opcao", "Testes do frontend."),
];

export const CATEGORY_ORDER: TechCategory[] = [
  "linguagem", "backend", "frontend", "mobile", "banco", "conector", "api", "ia", "infra", "qualidade",
];

export type IconData = { path: string; hex: string };

/** Ícones (Simple Icons) para uma lista de slugs do catálogo do backend; slugs sem logo livre ficam de fora. */
export function iconsFor(slugs: Iterable<string>): Record<string, IconData> {
  const out: Record<string, IconData> = {};
  for (const slug of slugs) {
    const found = icon(slug);
    if (found) out[slug] = found;
  }
  return out;
}
