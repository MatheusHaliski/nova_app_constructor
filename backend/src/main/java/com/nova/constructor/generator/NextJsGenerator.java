package com.nova.constructor.generator;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Estrutura inicial de um frontend Next.js (App Router), com uma rota por tela informada no formulário. */
@Component
public class NextJsGenerator implements StackGenerator {

    @Override
    public String id() {
        return "nextjs";
    }

    @Override
    public void generate(GenerationPlan plan, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        boolean tailwind = spec.frontend().packages().contains("tailwind");
        files.put(root + "package.json", NestJsGenerator.packageJson(plan.slug() + "-web", plan.frontend().dependencies(),
                plan.frontend().plugins(), Map.of("dev", "next dev", "build", "next build", "start", "next start", "lint", "tsc --noEmit"), null));
        files.put(root + "tsconfig.json", """
                {
                  "compilerOptions": {
                    "target": "ES2022",
                    "lib": ["dom", "dom.iterable", "esnext"],
                    "strict": true,
                    "noEmit": true,
                    "module": "esnext",
                    "moduleResolution": "bundler",
                    "resolveJsonModule": true,
                    "isolatedModules": true,
                    "jsx": "preserve",
                    "incremental": true,
                    "skipLibCheck": true,
                    "plugins": [{ "name": "next" }],
                    "paths": { "@/*": ["./src/*"] }
                  },
                  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
                  "exclude": ["node_modules"]
                }
                """);
        files.put(root + "next.config.ts", """
                import type { NextConfig } from "next";

                const nextConfig: NextConfig = {
                  output: "standalone",
                };

                export default nextConfig;
                """);
        if (tailwind) {
            files.put(root + "postcss.config.mjs", "const config = {\n  plugins: { \"@tailwindcss/postcss\": {} },\n};\n\nexport default config;\n");
        }
        files.put(root + "src/app/globals.css", (tailwind ? "@import \"tailwindcss\";\n\n" : "") + """
                :root {
                  color-scheme: light dark;
                }

                body {
                  margin: 0;
                  font-family: system-ui, sans-serif;
                }
                """);
        files.put(root + "src/app/layout.tsx", Tpl.fill("""
                import type { Metadata } from "next";
                import "./globals.css";

                export const metadata: Metadata = {
                  title: {{name}},
                  description: {{description}},
                };

                export default function RootLayout({ children }: { children: React.ReactNode }) {
                  return (
                    <html lang="pt-BR">
                      <body>{children}</body>
                    </html>
                  );
                }
                """, Map.of("name", Tpl.jsonString(spec.name()),
                "description", Tpl.jsonString(spec.description() == null ? spec.name() : spec.description()))));
        files.put(root + "src/lib/api.ts", """
                /** Cliente da API do backend. A URL vem de API_URL (só no servidor). */
                const API_URL = process.env.API_URL ?? "http://localhost:8080";

                export async function api<T>(path: string, init?: RequestInit): Promise<T> {
                  const response = await fetch(`${API_URL}${path}`, { ...init, cache: "no-store" });
                  if (!response.ok) {
                    throw new Error(`API respondeu ${response.status} em ${path}`);
                  }
                  return (await response.json()) as T;
                }
                """);

        List<String> screens = spec.screensOrEmpty();
        StringBuilder links = new StringBuilder();
        for (String screen : screens) {
            String route = PlanService.slug(screen);
            links.append("        <li>\n          <Link href=\"/").append(route).append("\">").append(jsx(screen)).append("</Link>\n        </li>\n");
            files.put(root + "src/app/" + route + "/page.tsx", Tpl.fill("""
                    export default function {{component}}Page() {
                      return (
                        <main style={{ padding: 32 }}>
                          <h1>{{title}}</h1>
                          <p>Tela inicial gerada pelo NOVA. Monte aqui os elementos desta tela.</p>
                        </main>
                      );
                    }
                    """, Map.of("component", Tpl.pascal(route), "title", jsx(screen))));
        }
        files.put(root + "src/app/page.tsx", Tpl.fill("""
                {{import}}export default function Home() {
                  return (
                    <main style={{ padding: 32 }}>
                      <h1>{{title}}</h1>
                      <p>{{description}}</p>
                {{nav}}    </main>
                  );
                }
                """, Map.of("import", screens.isEmpty() ? "" : "import Link from \"next/link\";\n\n",
                "title", jsx(spec.name()),
                "description", jsx(spec.description() == null ? "Projeto gerado pelo NOVA." : spec.description()),
                "nav", screens.isEmpty() ? "" : "      <ul>\n" + links + "      </ul>\n")));
        if (spec.hasFeature("docker")) {
            files.put(root + "Dockerfile", """
                    FROM node:22-alpine AS build
                    WORKDIR /app
                    COPY package.json ./
                    RUN npm install
                    COPY . .
                    RUN npm run build

                    FROM node:22-alpine
                    WORKDIR /app
                    ENV NODE_ENV=production PORT=3000 HOSTNAME=0.0.0.0
                    COPY --from=build /app/.next/standalone ./
                    COPY --from=build /app/.next/static ./.next/static
                    EXPOSE 3000
                    CMD ["node", "server.js"]
                    """);
        }
    }

    /** Texto seguro para aparecer dentro de JSX. */
    static String jsx(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("{", "&#123;").replace("}", "&#125;");
    }
}
