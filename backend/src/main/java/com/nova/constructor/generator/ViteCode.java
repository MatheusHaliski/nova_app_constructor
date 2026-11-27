package com.nova.constructor.generator;

import com.nova.constructor.generator.ProjectSpec.EntitySpec;
import com.nova.constructor.generator.ProjectSpec.ScreenSpec;
import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Names;
import com.nova.constructor.insight.SpringCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Telas de um frontend React com Vite: roteador sem dependências (rotas com parâmetros como /produtos/:id), menu, tema
 * e uma página por modelo de tela ligada à API pelo proxy /api do Vite. A rota pode ser definida pela prévia do NOVA.
 */
final class ViteCode {

    private ViteCode() {}

    static void generate(GenerationPlan plan, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        List<ScreenSpec> screens = new ArrayList<>(spec.screenSpecsOrEmpty());
        if (screens.isEmpty()) {
            spec.screensOrEmpty().forEach(n -> screens.add(new ScreenSpec(n, "BLANK", null, "PUBLIC", true, null)));
        }
        files.put(root + "src/main.tsx", """
                import { StrictMode } from "react";
                import { createRoot } from "react-dom/client";
                import { App } from "./App";
                import "./index.css";

                createRoot(document.getElementById("root")!).render(
                  <StrictMode>
                    <App />
                  </StrictMode>,
                );
                """);
        String css = spec.theme() == null ? "body {\n  margin: 0;\n  font-family: system-ui, sans-serif;\n}\n" : DomainCode.themeCss(spec.theme()).stripLeading();
        String fontImport = css.lines().filter(l -> l.startsWith("@import url(")).findFirst().orElse(null);
        files.put(root + "src/index.css", fontImport == null ? css : fontImport + "\n" + css.replace(fontImport + "\n", ""));
        files.put(root + "src/lib/api.ts", """
                /** Cliente da API: no desenvolvimento, o Vite repassa /api para o backend (veja vite.config.ts). */
                export async function api<T>(path: string, init?: RequestInit): Promise<T> {
                  const response = await fetch(path, init);
                  if (!response.ok) {
                    throw new Error(`API respondeu ${response.status} em ${path}`);
                  }
                  return (await response.json()) as T;
                }
                """);
        files.put(root + "src/router.tsx", """
                import { useEffect, useState, type MouseEvent, type ReactNode } from "react";

                /** Roteador mínimo, sem dependências. A prévia do NOVA define a rota em window.__NOVA_ROUTE__. */
                type NovaWindow = Window & { __NOVA_ROUTE__?: string };

                export function currentPath(): string {
                  return (window as NovaWindow).__NOVA_ROUTE__ ?? window.location.pathname;
                }

                export function navigate(to: string) {
                  const w = window as NovaWindow;
                  if (w.__NOVA_ROUTE__ !== undefined) {
                    w.__NOVA_ROUTE__ = to;
                  } else {
                    window.history.pushState(null, "", to);
                  }
                  window.dispatchEvent(new PopStateEvent("popstate"));
                }

                export function usePath(): string {
                  const [path, setPath] = useState(currentPath);
                  useEffect(() => {
                    const update = () => setPath(currentPath());
                    window.addEventListener("popstate", update);
                    return () => window.removeEventListener("popstate", update);
                  }, []);
                  return path;
                }

                /** Compara "/produtos/:id" com "/produtos/7" e devolve os parâmetros, ou null. */
                export function match(pattern: string, path: string): Record<string, string> | null {
                  const a = pattern.split("/");
                  const b = path.replace(/\\/$/, "").split("/");
                  if (a.length !== b.length) return null;
                  const params: Record<string, string> = {};
                  for (let i = 0; i < a.length; i++) {
                    if (a[i].startsWith(":")) params[a[i].slice(1)] = decodeURIComponent(b[i]);
                    else if (a[i] !== b[i]) return null;
                  }
                  return params;
                }

                export function Link({ to, children, className }: { to: string; children: ReactNode; className?: string }) {
                  const onClick = (e: MouseEvent<HTMLAnchorElement>) => {
                    e.preventDefault();
                    navigate(to);
                  };
                  return (
                    <a href={to} onClick={onClick} className={className}>
                      {children}
                    </a>
                  );
                }
                """);
        StringBuilder imports = new StringBuilder();
        StringBuilder routes = new StringBuilder();
        StringBuilder nav = new StringBuilder();
        StringBuilder cards = new StringBuilder();
        for (ScreenSpec s : screens) {
            String slug = PlanService.slug(s.name());
            String component = Names.pascal(slug) + "Page";
            EntitySpec entity = spec.entitiesOrEmpty().stream().filter(e -> e.name().equals(s.entity())).findFirst().orElse(null);
            String path = "/" + slug + ("DETAIL".equals(s.template()) ? "/:id" : "");
            files.put(root + "src/pages/" + component + ".tsx", page(s, entity, component, screens));
            imports.append("import { ").append(component).append(" } from \"./pages/").append(component).append("\";\n");
            routes.append("  { path: \"").append(path).append("\", component: ").append(component).append(" },\n");
            if (Boolean.TRUE.equals(s.inMenu())) {
                nav.append("          <Link to=\"/").append(slug).append("\">").append(SpringCode.jsx(s.name())).append("</Link>\n");
                cards.append("        <li>\n          <Link to=\"/").append(slug).append("\" className=\"app-card\">").append(SpringCode.jsx(s.name()))
                        .append("</Link>\n        </li>\n");
            }
        }
        files.put(root + "src/routes.tsx", "import type { ComponentType } from \"react\";\n" + imports
                + "\nexport type PageProps = { params: Record<string, string> };\n\n"
                + "/** Rotas do app: uma por tela caracterizada no NOVA. */\n"
                + "export const routes: { path: string; component: ComponentType<PageProps> }[] = [\n" + routes + "];\n");
        files.put(root + "src/App.tsx", """
                import { routes } from "./routes";
                import { Link, match, usePath } from "./router";

                function Home() {
                  return (
                    <main className="app-main">
                      <h1>%s</h1>
                      <p>%s</p>
                      <ul className="app-cards">
                %s      </ul>
                    </main>
                  );
                }

                export function App() {
                  const path = usePath();
                  let page = path === "/" ? <Home /> : null;
                  for (const route of routes) {
                    const params = page ? null : match(route.path, path);
                    if (params) {
                      const Page = route.component;
                      page = <Page params={params} />;
                    }
                  }
                  return (
                    <>
                      <header className="app-header">
                        <Link to="/" className="app-brand">
                          %s
                        </Link>
                        <nav aria-label="Menu principal" className="app-nav">
                %s        </nav>
                      </header>
                      {page ?? (
                        <main className="app-main">
                          <h1>Página não encontrada</h1>
                        </main>
                      )}
                    </>
                  );
                }
                """.formatted(SpringCode.jsx(spec.name()), SpringCode.jsx(spec.description() == null ? "Projeto gerado pelo NOVA." : spec.description()),
                cards, SpringCode.jsx(spec.name()), nav));
    }

    private static String page(ScreenSpec s, EntitySpec e, String component, List<ScreenSpec> screens) {
        String title = SpringCode.jsx(s.name());
        if (e == null || !List.of("LIST", "DETAIL", "FORM").contains(s.template())) {
            String what = switch (s.template()) {
                case "LOGIN" -> "Entrada com usuário e senha.";
                case "SIGNUP" -> "Cadastro de novas pessoas.";
                case "SETTINGS" -> "Preferências e dados da conta.";
                case "DASHBOARD" -> "Painel com os números do app.";
                case "LANDING" -> "Apresentação do app.";
                default -> "Tela inicial gerada pelo NOVA. Monte aqui os elementos desta tela.";
            };
            return "export function " + component + "() {\n  return (\n    <main className=\"app-main\">\n      <h1>" + title + "</h1>\n      <p>" + what + "</p>\n"
                    + (s.purpose() == null ? "" : "      <p>" + SpringCode.jsx(s.purpose()) + "</p>\n") + "    </main>\n  );\n}\n";
        }
        String api = DomainRules.apiPath(e.name());
        List<SpringCode.F> fields = DomainRules.fields(e);
        String type = "type " + e.name() + " = { id: number | string } & Record<string, string | number | boolean | null | undefined>;\n";
        return switch (s.template()) {
            case "LIST" -> {
                String detail = screens.stream().filter(x -> "DETAIL".equals(x.template()) && e.name().equals(x.entity()))
                        .map(x -> "/" + PlanService.slug(x.name())).findFirst().orElse(null);
                List<SpringCode.F> columns = fields.stream().filter(f -> f.kind() != FieldKind.LONG_TEXT).limit(6).toList();
                StringBuilder head = new StringBuilder();
                StringBuilder cells = new StringBuilder();
                for (SpringCode.F f : columns) {
                    head.append("              <th>").append(SpringCode.jsx(SpringCode.label(f.name()))).append("</th>\n");
                    cells.append("                <td>").append(SpringCode.cell(f)).append("</td>\n");
                }
                if (detail != null) {
                    head.append("              <th>Código</th>\n");
                    cells.append("                <td>\n                  <Link to={`").append(detail).append("/${item.id}`}>#{item.id}</Link>\n                </td>\n");
                }
                yield "import { useEffect, useState } from \"react\";\nimport { api } from \"../lib/api\";\n"
                        + (detail != null ? "import { Link } from \"../router\";\n" : "") + "\n" + type + "\n"
                        + "/** " + title + ": lista de " + e.name() + " vinda de " + api + ". Gerado pelo NOVA. */\n"
                        + "export function " + component + "() {\n"
                        + "  const [itens, setItens] = useState<" + e.name() + "[] | null>(null);\n  const [erro, setErro] = useState<string | null>(null);\n\n"
                        + "  useEffect(() => {\n    api<" + e.name() + "[]>(\"" + api + "\")\n      .then(setItens)\n"
                        + "      .catch(() => setErro(\"Não foi possível carregar os dados agora. Tente de novo em instantes.\"));\n  }, []);\n\n"
                        + "  return (\n    <main className=\"app-main\">\n      <h1>" + title + "</h1>\n"
                        + "      {erro ? (\n        <p role=\"alert\">{erro}</p>\n      ) : itens === null ? (\n        <p>Carregando…</p>\n"
                        + "      ) : itens.length === 0 ? (\n        <p>Nenhum item cadastrado ainda.</p>\n      ) : (\n        <table>\n          <thead>\n            <tr>\n"
                        + head + "            </tr>\n          </thead>\n          <tbody>\n            {itens.map((item) => (\n              <tr key={item.id}>\n" + cells
                        + "              </tr>\n            ))}\n          </tbody>\n        </table>\n      )}\n    </main>\n  );\n}\n";
            }
            case "DETAIL" -> {
                StringBuilder rows = new StringBuilder();
                for (SpringCode.F f : fields) {
                    rows.append("          <dt>").append(SpringCode.jsx(SpringCode.label(f.name()))).append("</dt>\n          <dd>").append(SpringCode.cell(f))
                            .append("</dd>\n");
                }
                yield "import { useEffect, useState } from \"react\";\nimport { api } from \"../lib/api\";\nimport type { PageProps } from \"../routes\";\n\n" + type + "\n"
                        + "/** " + title + ": um item de " + e.name() + " vindo de " + api + "/:id. Gerado pelo NOVA. */\n"
                        + "export function " + component + "({ params }: PageProps) {\n"
                        + "  const [item, setItem] = useState<" + e.name() + " | null>(null);\n  const [erro, setErro] = useState<string | null>(null);\n\n"
                        + "  useEffect(() => {\n    api<" + e.name() + ">(`" + api + "/${encodeURIComponent(params.id)}`)\n      .then(setItem)\n"
                        + "      .catch(() => setErro(\"Item não encontrado.\"));\n  }, [params.id]);\n\n"
                        + "  return (\n    <main className=\"app-main\">\n      <h1>" + title + " #{params.id}</h1>\n"
                        + "      {erro ? (\n        <p role=\"alert\">{erro}</p>\n      ) : item === null ? (\n        <p>Carregando…</p>\n      ) : (\n        <dl>\n" + rows
                        + "        </dl>\n      )}\n    </main>\n  );\n}\n";
            }
            default -> {
                String after = screens.stream().filter(x -> "LIST".equals(x.template()) && e.name().equals(x.entity()))
                        .map(x -> "/" + PlanService.slug(x.name())).findFirst().orElse(null);
                StringBuilder body = new StringBuilder();
                StringBuilder inputs = new StringBuilder();
                for (SpringCode.F f : fields) {
                    if (f.statusField()) {
                        continue;
                    }
                    String read = "dados.get(\"" + f.name() + "\")";
                    String value = switch (f.kind()) {
                        case INTEGER, DECIMAL, LONG, DOUBLE -> read + " ? Number(" + read + ") : null";
                        case BOOLEAN -> read + " === \"on\"";
                        default -> read + " || null";
                    };
                    body.append("      ").append(f.name()).append(": ").append(value).append(",\n");
                    String label = SpringCode.jsx(SpringCode.label(f.name()));
                    String inputType = switch (f.kind()) {
                        case INTEGER, LONG -> "number\" step=\"1";
                        case DECIMAL, DOUBLE -> "number\" step=\"0.01";
                        case DATE -> "date";
                        case DATETIME -> "datetime-local";
                        default -> "text";
                    };
                    if (f.kind() == FieldKind.BOOLEAN) {
                        inputs.append("        <label>\n          <input type=\"checkbox\" name=\"").append(f.name()).append("\" /> ").append(label).append("\n        </label>\n");
                    } else if (f.kind() == FieldKind.LONG_TEXT) {
                        inputs.append("        <label>\n          ").append(label).append("\n          <textarea name=\"").append(f.name()).append("\" rows={4}")
                                .append(f.required() ? " required" : "").append(" />\n        </label>\n");
                    } else {
                        inputs.append("        <label>\n          ").append(label).append("\n          <input type=\"").append(inputType).append("\" name=\"")
                                .append(f.name()).append("\"").append(f.required() ? " required" : "").append(" />\n        </label>\n");
                    }
                }
                yield "import { useState, type FormEvent } from \"react\";\n" + (after != null ? "import { navigate } from \"../router\";\n" : "") + "\n"
                        + "/** " + title + ": cria um item de " + e.name() + " com POST " + api + ". Gerado pelo NOVA. */\n"
                        + "export function " + component + "() {\n  const [mensagem, setMensagem] = useState<string | null>(null);\n\n"
                        + "  async function salvar(e: FormEvent<HTMLFormElement>) {\n    e.preventDefault();\n    const dados = new FormData(e.currentTarget);\n"
                        + "    const corpo = {\n" + body + "    };\n"
                        + "    const resposta = await fetch(\"" + api + "\", { method: \"POST\", headers: { \"Content-Type\": \"application/json\" }, body: JSON.stringify(corpo) });\n"
                        + "    if (resposta.status === 401) {\n      setMensagem(\"Faça login para salvar.\");\n    } else if (!resposta.ok) {\n"
                        + "      setMensagem(`Não foi possível salvar (erro ${resposta.status}). Confira os campos.`);\n    } else {\n"
                        + (after != null ? "      navigate(\"" + after + "\");\n" : "      setMensagem(\"Salvo.\");\n") + "    }\n  }\n\n"
                        + "  return (\n    <main className=\"app-main\">\n      <h1>" + title + "</h1>\n      {mensagem && <p role=\"alert\">{mensagem}</p>}\n"
                        + "      <form onSubmit={salvar} style={{ display: \"grid\", gap: 12, maxWidth: 480 }}>\n" + inputs
                        + "        <button type=\"submit\">Salvar</button>\n      </form>\n    </main>\n  );\n}\n";
            }
        };
    }
}
