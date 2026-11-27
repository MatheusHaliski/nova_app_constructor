package com.nova.constructor.insight;

import com.nova.constructor.insight.Model.Role;
import com.nova.constructor.insight.Model.Screen;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lê o frontend (Next.js ou React com Vite): páginas e rotas, chamadas à API e tratamento de lista vazia e de erro. */
final class FrontendReader {

    static final String PLACEHOLDER = "Tela inicial gerada pelo NOVA";
    private static final Pattern API_CALL = Pattern.compile("[\"'`](/api/[^\"'`\\s?]*)");
    private static final Pattern H1 = Pattern.compile("<h1[^>]*>\\s*([^<{]+?)\\s*</h1>");
    private static final Pattern EMPTY = Pattern.compile("\\.length\\s*===?\\s*0|\\.length\\s*<\\s*1|!\\s*[\\w.?]+\\.length\\b|\\.length\\s*\\?|(?i)nenhum|nenhuma|vazi[oa]|empty");
    private static final Pattern ERROR = Pattern.compile("\\bcatch\\b|\\berror\\b|\\bErro\\b|\\berro\\b|isError|onError|error\\.tsx|notFound\\(");
    private static final Pattern ROUTE = Pattern.compile("<Route\\s+[^>]*path=[\"']([^\"']+)[\"'][^>]*element=\\{\\s*<(\\w+)");

    private FrontendReader() {}

    static void read(Sources src, Facts facts) {
        boolean next = src.any("frontend/(src/)?app/.*page\\.(tsx|jsx|ts|js)");
        boolean vite = src.has("frontend/vite.config.ts") || src.has("frontend/vite.config.js");
        if (!next && !vite) {
            return;
        }
        facts.frontendFramework = next ? "nextjs" : "react-vite";
        for (String p : src.paths("frontend/.+\\.(tsx|jsx|ts|js)")) {
            if (p.contains("/node_modules/") || p.endsWith(".d.ts") || p.matches(".*\\.config\\.(ts|js|mjs)")) {
                continue;
            }
            String text = src.get(p);
            String file = Sources.fileName(p);
            if (file.matches(".*\\.(test|spec)\\.(tsx|ts|jsx|js)")) {
                facts.testCount++;
                facts.items.add(new Facts.Item(Role.TEST, file, p, "teste do frontend"));
            } else if (next && file.matches("page\\.(tsx|jsx|ts|js)")) {
                facts.screens.add(screen(nextRoute(p), p, text, src));
            } else if (next && file.matches("(layout|loading|error|not-found)\\.(tsx|jsx)")) {
                facts.items.add(new Facts.Item(Role.COMPONENT, file, p, file.startsWith("layout") ? "moldura comum das páginas"
                        : file.startsWith("error") ? "tela de erro da rota" : file.startsWith("loading") ? "carregamento da rota" : "página não encontrada"));
            } else if (p.contains("/components/")) {
                facts.items.add(new Facts.Item(Role.COMPONENT, file.replaceAll("\\.(tsx|jsx|ts|js)$", ""), p, "componente"));
            } else if (vite && file.matches("routes\\.(tsx|ts)")) {
                // Rotas do roteador gerado pelo NOVA: { path: "/produtos/:id", component: ProdutoPage }
                Matcher r = Pattern.compile("path:\\s*[\"']([^\"']+)[\"']\\s*,\\s*component:\\s*(\\w+)").matcher(text);
                while (r.find()) {
                    String component = r.group(2);
                    String target = src.paths("frontend/src/.*\\b" + component + "\\.(tsx|jsx)").stream().findFirst().orElse(p);
                    facts.screens.add(screen(r.group(1), target, src.get(target), src));
                }
            } else if (vite && p.contains("/pages/") && src.has("frontend/src/routes.tsx")) {
                continue;
            } else if (vite && file.matches("App\\.(tsx|jsx)") && src.has("frontend/src/routes.tsx")) {
                facts.screens.add(screen("/", p, text, src));
            } else if (vite && (p.contains("/pages/") && !src.has("frontend/src/routes.tsx") || file.matches("App\\.(tsx|jsx)"))) {
                Matcher r = ROUTE.matcher(text);
                boolean routed = false;
                while (r.find()) {
                    routed = true;
                    String component = r.group(2);
                    String target = src.paths("frontend/src/.*\\b" + component + "\\.(tsx|jsx)").stream().findFirst().orElse(p);
                    facts.screens.add(screen(r.group(1), target, src.get(target), src));
                }
                if (!routed && file.matches("App\\.(tsx|jsx)")) {
                    facts.screens.add(screen("/", p, text, src));
                }
            } else if (p.contains("/lib/") || p.contains("/services/") || p.contains("/api/")) {
                facts.items.add(new Facts.Item(Role.SERVICE, file, p, API_CALL.matcher(text).find() || text.contains("fetch(")
                        ? "acesso à API" : "funções de apoio"));
            } else if (!file.startsWith("main.")) {
                facts.items.add(new Facts.Item(Role.OTHER, file, p, "código do frontend"));
            }
        }
        facts.screens.sort((a, b) -> a.route().compareTo(b.route()));
    }

    /** Rota do App Router a partir do caminho de page.tsx (grupos entre parênteses não entram na URL). */
    static String nextRoute(String path) {
        String rel = path.replaceFirst("^frontend/(src/)?app/?", "").replaceAll("/?page\\.(tsx|jsx|ts|js)$", "");
        StringBuilder route = new StringBuilder();
        for (String part : rel.split("/")) {
            if (part.isEmpty() || part.startsWith("(") && part.endsWith(")")) {
                continue;
            }
            route.append('/').append(part);
        }
        return route.isEmpty() ? "/" : route.toString();
    }

    private static Screen screen(String route, String path, String text, Sources src) {
        List<String> calls = new ArrayList<>();
        Matcher m = API_CALL.matcher(text);
        while (m.find()) {
            String call = m.group(1).replaceAll("\\$\\{[^}]*\\}", "{id}");
            if (!calls.contains(call)) {
                calls.add(call);
            }
        }
        String title = Sources.first(H1, text, 1);
        boolean siblingError = src.any(java.util.regex.Pattern.quote(path.substring(0, path.lastIndexOf('/') + 1)) + "error\\.(tsx|jsx)");
        // Lista vazia só importa em telas que mostram uma coleção (percorrem itens com map).
        boolean showsList = text.contains(".map(");
        return new Screen(route, path, title == null ? null : title.trim(), calls, !showsList || EMPTY.matcher(text).find(),
                siblingError || ERROR.matcher(text).find(), text.contains(PLACEHOLDER));
    }
}
