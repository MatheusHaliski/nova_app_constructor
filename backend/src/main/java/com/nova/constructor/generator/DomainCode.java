package com.nova.constructor.generator;

import com.nova.constructor.generator.ProjectSpec.EntitySpec;
import com.nova.constructor.generator.ProjectSpec.ScreenSpec;
import com.nova.constructor.generator.ProjectSpec.Theme;
import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Model.Operation;
import com.nova.constructor.insight.Names;
import com.nova.constructor.insight.SpringCode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Código dos dados do domínio: entidades, repositórios, API CRUD, rotas de status, migrações e testes no Spring Boot;
 * no Next.js, menu, tema por tokens e uma página por modelo de tela ligada à API.
 */
final class DomainCode {

    private DomainCode() {}

    static void spring(GenerationPlan plan, String pkg, String pkgPath, String testPath, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        if (spec.entitiesOrEmpty().isEmpty()) {
            return;
        }
        List<String> packages = spec.backend().packages() == null ? List.of() : spec.backend().packages();
        boolean jpa = spec.database() != null && packages.contains("data-jpa");
        boolean migrations = jpa && packages.contains("flyway") && List.of("mysql", "postgresql").contains(spec.database().id());
        boolean tests = spec.hasFeature("tests");
        int version = 2;
        List<String[]> publicRules = new ArrayList<>();
        for (EntitySpec e : spec.entitiesOrEmpty()) {
            String module = e.name().toLowerCase(java.util.Locale.ROOT);
            String modPkg = pkg + "." + module;
            String dir = pkgPath + module + "/";
            String testDir = testPath + module + "/";
            String path = DomainRules.apiPath(e.name());
            String table = DomainRules.table(e.name());
            List<SpringCode.F> fields = DomainRules.fields(e);
            Set<Operation> ops = EnumSet.allOf(Operation.class);
            files.put(dir + e.name() + ".java", SpringCode.entity(modPkg, e.name(), table, fields, jpa));
            files.put(dir + e.name() + "Repository.java", SpringCode.repository(modPkg, e.name(), jpa));
            files.put(dir + e.name() + "Controller.java", SpringCode.controller(modPkg, e.name() + "Controller", e.name(), path, ops, fields, "Long", Set.of()));
            if (tests) {
                files.put(testDir + e.name() + "ControllerTest.java",
                        SpringCode.resourceTest(modPkg, e.name() + "Controller", e.name(), path, ops, fields, "Long", Set.of()));
            }
            for (SpringCode.F f : fields) {
                if (!f.statusField() || f.values() == null || f.values().size() < 2) {
                    continue;
                }
                String className = e.name() + Names.pascal(f.name()) + "Controller";
                String statusPath = path + "/{id}/" + Names.kebab(f.name());
                List<String> allowed = f.values().subList(1, f.values().size());
                files.put(dir + className + ".java", SpringCode.statusController(modPkg, className, e.name(), f, "PATCH", statusPath, "id", allowed, "Long", Set.of()));
                if (tests) {
                    files.put(testDir + className + "Test.java",
                            SpringCode.statusTest(modPkg, className, e.name(), f, "PATCH", statusPath, "id", allowed, f.values(), "Long", Set.of()));
                }
            }
            if (migrations) {
                files.put(root + "src/main/resources/db/migration/V" + version++ + "__criar_" + table + ".sql",
                        SpringCode.migration(spec.database().id(), table, fields));
            }
            if (readsArePublic(spec, e.name())) {
                publicRules.add(new String[] {"GET", path});
                publicRules.add(new String[] {"GET", path + "/**"});
            }
        }
        String security = pkgPath + "security/SecurityConfig.java";
        if (files.containsKey(security) && !publicRules.isEmpty()) {
            String edited = SpringCode.permit(files.get(security), publicRules);
            if (edited != null) {
                files.put(security, edited);
            }
        }
    }

    /** Leitura pública, a menos que todas as telas que mostram a entidade exijam login. */
    private static boolean readsArePublic(ProjectSpec spec, String entity) {
        List<ScreenSpec> using = spec.screenSpecsOrEmpty().stream().filter(s -> entity.equals(s.entity())).toList();
        return using.isEmpty() || using.stream().anyMatch(s -> "PUBLIC".equals(s.access()));
    }

    // ------------------------------------------------------------------------------------------- Next.js

    static void next(GenerationPlan plan, String root, Map<String, String> files) {
        ProjectSpec spec = plan.spec();
        Theme theme = spec.theme();
        if (theme != null) {
            String css = files.getOrDefault(root + "src/app/globals.css", "");
            files.put(root + "src/app/globals.css", css + themeCss(theme));
        }
        List<ScreenSpec> screens = spec.screenSpecsOrEmpty();
        if (screens.isEmpty()) {
            return;
        }
        StringBuilder nav = new StringBuilder();
        for (ScreenSpec s : screens) {
            if (Boolean.TRUE.equals(s.inMenu())) {
                nav.append("            <Link href=\"/").append(PlanService.slug(s.name())).append("\">").append(SpringCode.jsx(s.name())).append("</Link>\n");
            }
        }
        files.put(root + "src/app/layout.tsx", """
                import type { Metadata } from "next";
                import Link from "next/link";
                import "./globals.css";

                export const metadata: Metadata = {
                  title: %s,
                  description: %s,
                };

                export default function RootLayout({ children }: { children: React.ReactNode }) {
                  return (
                    <html lang="pt-BR">
                      <body>
                        <header className="app-header">
                          <Link href="/" className="app-brand">
                            %s
                          </Link>
                          <nav aria-label="Menu principal" className="app-nav">
                %s          </nav>
                        </header>
                        {children}
                      </body>
                    </html>
                  );
                }
                """.formatted(Tpl.jsonString(spec.name()), Tpl.jsonString(spec.description() == null ? spec.name() : spec.description()),
                SpringCode.jsx(spec.name()), nav));
        StringBuilder cards = new StringBuilder();
        for (ScreenSpec s : screens) {
            if (Boolean.TRUE.equals(s.inMenu())) {
                cards.append("        <li>\n          <Link href=\"/").append(PlanService.slug(s.name())).append("\" className=\"app-card\">")
                        .append(SpringCode.jsx(s.name())).append("</Link>\n        </li>\n");
            }
        }
        files.put(root + "src/app/page.tsx", """
                import Link from "next/link";

                export default function Home() {
                  return (
                    <main className="app-main">
                      <h1>%s</h1>
                      <p>%s</p>
                      <ul className="app-cards">
                %s      </ul>
                    </main>
                  );
                }
                """.formatted(SpringCode.jsx(spec.name()), SpringCode.jsx(spec.description() == null ? "Projeto gerado pelo NOVA." : spec.description()),
                cards));
        for (ScreenSpec s : screens) {
            String slug = PlanService.slug(s.name());
            String dir = root + "src/app/" + slug + "/";
            EntitySpec entity = spec.entitiesOrEmpty().stream().filter(e -> e.name().equals(s.entity())).findFirst().orElse(null);
            switch (s.template()) {
                case "LIST" -> {
                    List<SpringCode.F> columns = DomainRules.fields(entity).stream().filter(f -> f.kind() != FieldKind.LONG_TEXT).limit(6).toList();
                    String detail = screens.stream().filter(x -> "DETAIL".equals(x.template()) && entity.name().equals(x.entity()))
                            .map(x -> "/" + PlanService.slug(x.name())).findFirst().orElse(null);
                    String page = SpringCode.nextListPage("/" + slug, s.name(), DomainRules.apiPath(entity.name()), entity.name(), columns);
                    if (detail != null) {
                        page = page.replaceFirst("</h1>\n", "</h1>\n      <p className=\"app-hint\">Clique no código de um item para ver os detalhes.</p>\n");
                        page = page.replace("            </tr>\n          </thead>", "              <th style={{ textAlign: \"left\", padding: \"8px 12px\" }}>Código</th>\n            </tr>\n          </thead>")
                                .replace("              </tr>\n            ))}", "                <td style={{ padding: \"8px 12px\" }}>\n                  <a href={`" + detail
                                        + "/${item.id}`}>#{item.id}</a>\n                </td>\n              </tr>\n            ))}");
                    }
                    files.put(dir + "page.tsx", page);
                }
                case "DETAIL" -> {
                    files.remove(dir + "page.tsx");
                    files.put(dir + "[id]/page.tsx", detailPage(s, entity));
                }
                case "FORM" -> files.put(dir + "page.tsx", formPage(s, entity, screens));
                case "DASHBOARD" -> files.put(dir + "page.tsx", dashboardPage(s, spec.entitiesOrEmpty()));
                default -> files.put(dir + "page.tsx", infoPage(s, spec.hasFeature("auth")));
            }
        }
    }

    private static String themeCss(Theme t) {
        String radius = switch (t.radius()) {
            case "NONE" -> "0";
            case "SM" -> "4px";
            case "LG" -> "16px";
            default -> "8px";
        };
        String space = "COMPACT".equals(t.density()) ? "12px" : "20px";
        String font = "system".equals(t.font()) ? "system-ui, -apple-system, \"Segoe UI\", sans-serif" : "\"" + t.font() + "\", system-ui, sans-serif";
        String importFont = "system".equals(t.font()) ? ""
                : "@import url(\"https://fonts.googleapis.com/css2?family=" + t.font().replace(' ', '+') + ":wght@400;600;700&display=swap\");\n";
        return "\n/* Tema escolhido no NOVA: mude os tokens aqui e todas as telas acompanham. */\n" + importFont
                + ":root {\n  --primary: " + t.primary() + ";\n  --radius: " + radius + ";\n  --space: " + space + ";\n  --font: " + font
                + ";\n  --text: #0f172a;\n  --muted: #5b6475;\n  --border: #e2e7f0;\n  --surface: #ffffff;\n  --background: #f6f8fb;\n}\n\n"
                + "body {\n  margin: 0;\n  font-family: var(--font);\n  color: var(--text);\n  background: var(--background);\n}\n\n"
                + "a {\n  color: var(--primary);\n}\n\n"
                + ".app-header {\n  display: flex;\n  flex-wrap: wrap;\n  align-items: center;\n  justify-content: space-between;\n  gap: var(--space);\n"
                + "  padding: 12px var(--space);\n  background: var(--surface);\n  border-bottom: 3px solid var(--primary);\n}\n\n"
                + ".app-brand {\n  font-weight: 700;\n  text-decoration: none;\n}\n\n"
                + ".app-nav {\n  display: flex;\n  flex-wrap: wrap;\n  gap: 16px;\n}\n\n"
                + ".app-main, main {\n  max-width: 1080px;\n  margin: 0 auto;\n  padding: calc(var(--space) * 1.5) var(--space) !important;\n}\n\n"
                + ".app-cards {\n  list-style: none;\n  padding: 0;\n  display: grid;\n  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));\n  gap: var(--space);\n}\n\n"
                + ".app-card {\n  display: block;\n  padding: var(--space);\n  background: var(--surface);\n  border: 1px solid var(--border);\n"
                + "  border-radius: var(--radius);\n  font-weight: 600;\n  text-decoration: none;\n}\n\n"
                + "table {\n  background: var(--surface);\n  border: 1px solid var(--border);\n  border-radius: var(--radius);\n}\n\n"
                + "th {\n  color: var(--muted);\n  font-size: 0.85rem;\n}\n\n"
                + "button, .app-button {\n  background: var(--primary);\n  color: #fff;\n  border: 0;\n  border-radius: var(--radius);\n  padding: 10px 18px;\n"
                + "  font: inherit;\n  font-weight: 600;\n  cursor: pointer;\n}\n\n"
                + "input, textarea, select {\n  font: inherit;\n  padding: 8px 10px;\n  border: 1px solid var(--border);\n  border-radius: var(--radius);\n}\n\n"
                + ".app-hint {\n  color: var(--muted);\n  font-size: 0.9rem;\n}\n\n"
                + "h1 {\n  font-size: 1.9rem;\n  font-weight: 700;\n  margin: 0 0 12px;\n}\n\n"
                + "h2 {\n  font-size: 1.3rem;\n  font-weight: 600;\n  margin: 24px 0 8px;\n}\n\n"
                + "dt {\n  color: var(--muted);\n  font-size: 0.85rem;\n  margin-top: 12px;\n}\n\n"
                + "dd {\n  margin: 2px 0 0;\n  font-size: 1.05rem;\n}\n\n"
                + "label {\n  display: grid;\n  gap: 4px;\n  font-weight: 600;\n}\n";
    }

    private static String detailPage(ScreenSpec s, EntitySpec e) {
        StringBuilder rows = new StringBuilder();
        for (SpringCode.F f : DomainRules.fields(e)) {
            rows.append("        <dt>").append(SpringCode.jsx(SpringCode.label(f.name()))).append("</dt>\n        <dd>").append(SpringCode.cell(f))
                    .append("</dd>\n");
        }
        String path = DomainRules.apiPath(e.name());
        return "import { notFound } from \"next/navigation\";\nimport { api } from \"@/lib/api\";\n\n"
                + "type " + e.name() + " = Record<string, string | number | boolean | null | undefined>;\n\n"
                + "/** " + SpringCode.jsx(s.name()) + ": um item de " + e.name() + " vindo de " + path + "/{id}. Gerado pelo NOVA. */\n"
                + "export const dynamic = \"force-dynamic\";\n\n"
                + "export default async function Detalhe({ params }: { params: Promise<{ id: string }> }) {\n"
                + "  const { id } = await params;\n  let item: " + e.name() + ";\n  try {\n"
                + "    item = await api<" + e.name() + ">(`" + path + "/${encodeURIComponent(id)}`);\n  } catch {\n    notFound();\n  }\n"
                + "  return (\n    <main style={{ padding: 32 }}>\n      <h1>" + SpringCode.jsx(s.name()) + " #{id}</h1>\n      <dl>\n" + rows
                + "      </dl>\n    </main>\n  );\n}\n";
    }

    private static String formPage(ScreenSpec s, EntitySpec e, List<ScreenSpec> screens) {
        String path = DomainRules.apiPath(e.name());
        String slug = PlanService.slug(s.name());
        String after = screens.stream().filter(x -> "LIST".equals(x.template()) && e.name().equals(x.entity()))
                .map(x -> "/" + PlanService.slug(x.name())).findFirst().orElse("/" + slug + "?ok=1");
        StringBuilder body = new StringBuilder();
        StringBuilder inputs = new StringBuilder();
        for (SpringCode.F f : DomainRules.fields(e)) {
            if (f.statusField()) {
                continue;
            }
            String read = "formData.get(\"" + f.name() + "\")";
            String value = switch (f.kind()) {
                case INTEGER, DECIMAL, LONG, DOUBLE -> read + " ? Number(" + read + ") : null";
                case BOOLEAN -> read + " === \"on\"";
                default -> read + " || null";
            };
            body.append("    ").append(f.name()).append(": ").append(value).append(",\n");
            String type = switch (f.kind()) {
                case INTEGER, LONG -> "number\" step=\"1";
                case DECIMAL, DOUBLE -> "number\" step=\"0.01";
                case DATE -> "date";
                case DATETIME -> "datetime-local";
                case BOOLEAN -> "checkbox";
                default -> "text";
            };
            String label = SpringCode.jsx(SpringCode.label(f.name()));
            if (f.kind() == FieldKind.LONG_TEXT) {
                inputs.append("        <label>\n          ").append(label).append("\n          <textarea name=\"").append(f.name()).append("\" rows={4}")
                        .append(f.required() ? " required" : "").append(" />\n        </label>\n");
            } else if (f.kind() == FieldKind.BOOLEAN) {
                inputs.append("        <label>\n          <input type=\"checkbox\" name=\"").append(f.name()).append("\" /> ").append(label).append("\n        </label>\n");
            } else {
                inputs.append("        <label>\n          ").append(label).append("\n          <input type=\"").append(type).append("\" name=\"")
                        .append(f.name()).append("\"").append(f.required() ? " required" : "").append(" />\n        </label>\n");
            }
        }
        return "import { redirect } from \"next/navigation\";\n\n"
                + "const API_URL = process.env.API_URL ?? \"http://localhost:8080\";\n\n"
                + "/** " + SpringCode.jsx(s.name()) + ": cria um item de " + e.name() + " com POST " + path + ". Gerado pelo NOVA. */\n"
                + "async function salvar(formData: FormData) {\n  \"use server\";\n  const dados = {\n" + body + "  };\n"
                + "  const resposta = await fetch(`${API_URL}" + path + "`, {\n    method: \"POST\",\n    headers: { \"Content-Type\": \"application/json\" },\n"
                + "    body: JSON.stringify(dados),\n    cache: \"no-store\",\n  });\n"
                + "  if (!resposta.ok) {\n    redirect(`/" + slug + "?erro=${resposta.status}`);\n  }\n  redirect(\"" + after + "\");\n}\n\n"
                + "export default async function Formulario({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {\n"
                + "  const { erro } = await searchParams;\n  return (\n    <main style={{ padding: 32 }}>\n      <h1>" + SpringCode.jsx(s.name()) + "</h1>\n"
                + "      {erro === \"401\" ? (\n        <p role=\"alert\">Faça login para salvar.</p>\n      ) : erro ? (\n"
                + "        <p role=\"alert\">Não foi possível salvar (erro {String(erro)}). Confira os campos e tente de novo.</p>\n      ) : null}\n"
                + "      <form action={salvar} style={{ display: \"grid\", gap: 12, maxWidth: 480 }}>\n" + inputs
                + "        <button type=\"submit\">Salvar</button>\n      </form>\n    </main>\n  );\n}\n";
    }

    private static String dashboardPage(ScreenSpec s, List<EntitySpec> entities) {
        StringBuilder fetches = new StringBuilder();
        StringBuilder cards = new StringBuilder();
        for (EntitySpec e : entities) {
            String var = Names.camel(e.name());
            fetches.append("  const ").append(var).append(" = await contar(\"").append(DomainRules.apiPath(e.name())).append("\");\n");
            cards.append("        <li className=\"app-card\">\n          <span className=\"app-hint\">").append(SpringCode.jsx(Names.plural(Names.kebab(e.name())).replace('-', ' ')))
                    .append("</span>\n          <strong style={{ display: \"block\", fontSize: 28 }}>{").append(var).append(" ?? \"—\"}</strong>\n        </li>\n");
        }
        return "import { api } from \"@/lib/api\";\n\nexport const dynamic = \"force-dynamic\";\n\n"
                + "async function contar(caminho: string): Promise<number | null> {\n  try {\n    return (await api<unknown[]>(caminho)).length;\n  } catch {\n    return null;\n  }\n}\n\n"
                + "/** " + SpringCode.jsx(s.name()) + ": totais de cada dado do app. Gerado pelo NOVA. */\n"
                + "export default async function Painel() {\n" + fetches + "  return (\n    <main style={{ padding: 32 }}>\n      <h1>" + SpringCode.jsx(s.name())
                + "</h1>\n      <ul className=\"app-cards\">\n" + cards + "      </ul>\n    </main>\n  );\n}\n";
    }

    private static String infoPage(ScreenSpec s, boolean auth) {
        String what = switch (s.template()) {
            case "LOGIN" -> auth ? "Entrada com usuário e senha. O backend já emite o token em POST /api/auth/token." : "Entrada no app.";
            case "SIGNUP" -> "Cadastro de novas pessoas.";
            case "SETTINGS" -> "Preferências e dados da conta.";
            case "LANDING" -> "Apresentação do app.";
            default -> "Tela inicial gerada pelo NOVA. Monte aqui os elementos desta tela.";
        };
        return """
                export default function %sPage() {
                  return (
                    <main style={{ padding: 32 }}>
                      <h1>%s</h1>
                      <p>%s</p>
                %s    </main>
                  );
                }
                """.formatted(Names.pascal(PlanService.slug(s.name())), SpringCode.jsx(s.name()), SpringCode.jsx(what),
                s.purpose() == null ? "" : "      <p>" + SpringCode.jsx(s.purpose()) + "</p>\n");
    }
}
