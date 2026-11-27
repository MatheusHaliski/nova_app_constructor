package com.nova.constructor.generator;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Estrutura inicial de um frontend React + Vite (SPA). */
@Component
public class ViteReactGenerator implements StackGenerator {

    @Override
    public String id() {
        return "react-vite";
    }

    @Override
    public void generate(GenerationPlan plan, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        files.put(root + "package.json", NestJsGenerator.packageJson(plan.slug() + "-web", plan.frontend().dependencies(),
                plan.frontend().plugins(), Map.of("dev", "vite", "build", "tsc --noEmit && vite build", "preview", "vite preview"), "module"));
        files.put(root + "tsconfig.json", """
                {
                  "compilerOptions": {
                    "target": "ES2022",
                    "lib": ["ES2022", "DOM", "DOM.Iterable"],
                    "module": "ESNext",
                    "moduleResolution": "bundler",
                    "jsx": "react-jsx",
                    "strict": true,
                    "noEmit": true,
                    "skipLibCheck": true,
                    "types": ["vite/client"]
                  },
                  "include": ["src"]
                }
                """);
        files.put(root + "vite.config.ts", """
                import react from "@vitejs/plugin-react";
                import { defineConfig } from "vite";

                export default defineConfig({
                  plugins: [react()],
                  server: {
                    port: 3000,
                    proxy: { "/api": process.env.API_URL ?? "http://localhost:8080" },
                  },
                });
                """);
        files.put(root + "index.html", Tpl.fill("""
                <!doctype html>
                <html lang="pt-BR">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>{{title}}</title>
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="/src/main.tsx"></script>
                  </body>
                </html>
                """, Map.of("title", NextJsGenerator.jsx(spec.name()))));
        files.put(root + "src/main.tsx", """
                import { StrictMode } from "react";
                import { createRoot } from "react-dom/client";
                import { App } from "./App";

                createRoot(document.getElementById("root")!).render(
                  <StrictMode>
                    <App />
                  </StrictMode>,
                );
                """);
        ViteCode.generate(plan, root, files);
        if (spec.hasFeature("docker")) {
            files.put(root + "Dockerfile", """
                    FROM node:22-alpine AS build
                    WORKDIR /app
                    COPY package.json ./
                    RUN npm install
                    COPY . .
                    RUN npm run build

                    FROM nginx:alpine
                    COPY --from=build /app/dist /usr/share/nginx/html
                    EXPOSE 80
                    """);
        }
    }
}
