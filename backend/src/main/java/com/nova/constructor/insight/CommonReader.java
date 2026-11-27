package com.nova.constructor.insight;

import com.nova.constructor.insight.Model.Dependencies;
import com.nova.constructor.insight.Model.EnvVar;
import com.nova.constructor.insight.Model.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** O que vale para qualquer stack: tabelas das migrações SQL, variáveis de ambiente e dependências declaradas. */
final class CommonReader {

    private static final Pattern CREATE_TABLE = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"]?(\\w+)[`\"]?\\s*\\(");
    private static final List<Pattern> ENV_USES = List.of(
            Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(?::[^}]*)?\\}"),
            Pattern.compile("System\\.getenv\\(\\s*\"([A-Z][A-Z0-9_]*)\""),
            Pattern.compile("process\\.env\\.([A-Z][A-Z0-9_]*)"),
            Pattern.compile("process\\.env\\[\\s*['\"]([A-Z][A-Z0-9_]*)['\"]"),
            Pattern.compile("import\\.meta\\.env\\.([A-Z][A-Z0-9_]*)"),
            Pattern.compile("os\\.(?:environ(?:\\.get)?|getenv)[\\[(]\\s*['\"]([A-Z][A-Z0-9_]*)['\"]"),
            Pattern.compile("\\benv\\(\\s*['\"]([A-Z][A-Z0-9_]*)['\"]"));
    /** Variáveis que o ambiente de execução já define. */
    private static final Set<String> RUNTIME = Set.of("NODE_ENV", "PORT", "HOSTNAME", "PATH", "HOME", "CI", "PWD", "TZ", "LANG", "VITE_USER_NODE_ENV",
            "MODE", "DEV", "PROD", "SSR", "BASE_URL");
    private static final Pattern SECRET_NAME = Pattern.compile("SECRET|PASSWORD|PASSWD|TOKEN|API_KEY|PRIVATE|CREDENTIAL|_KEY$");

    private CommonReader() {}

    static List<Table> tables(Sources src) {
        List<Table> tables = new ArrayList<>();
        for (String p : src.paths(".+\\.sql")) {
            String text = src.get(p);
            Matcher m = CREATE_TABLE.matcher(text);
            while (m.find()) {
                String body = SpringReader.balanced(text, m.end() - 1);
                if (body == null) {
                    continue;
                }
                List<String> columns = new ArrayList<>();
                int depth = 0;
                StringBuilder part = new StringBuilder();
                for (char c : (body + ",").toCharArray()) {
                    if (c == '(') {
                        depth++;
                    } else if (c == ')') {
                        depth--;
                    }
                    if (c == ',' && depth == 0) {
                        String col = part.toString().replaceAll("--[^\n]*", "").trim();
                        part.setLength(0);
                        String first = col.split("\\s+")[0].replaceAll("[`\"]", "");
                        if (!first.isEmpty() && !first.matches("(?i)CONSTRAINT|PRIMARY|FOREIGN|UNIQUE|INDEX|KEY|CHECK")) {
                            columns.add(first);
                        }
                    } else {
                        part.append(c);
                    }
                }
                tables.add(new Table(m.group(1), p, columns));
            }
        }
        return tables;
    }

    /** Variáveis lidas pelo código e as documentadas no .env.example. */
    static List<EnvVar> env(Sources src) {
        Map<String, Set<String>> used = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : src.all().entrySet()) {
            String path = e.getKey();
            String name = Sources.fileName(path);
            if (name.startsWith(".env") || path.endsWith(".md") || path.contains("/node_modules/") || path.startsWith(".github/")
                    || !name.matches(".+\\.(java|kt|ts|tsx|js|jsx|mjs|py|yml|yaml|properties)") && !name.equals("Dockerfile")) {
                continue;
            }
            for (Pattern p : ENV_USES) {
                Matcher m = p.matcher(e.getValue());
                while (m.find()) {
                    if (!RUNTIME.contains(m.group(1))) {
                        used.computeIfAbsent(m.group(1), k -> new LinkedHashSet<>()).add(path);
                    }
                }
            }
        }
        Set<String> documented = documented(src);
        List<EnvVar> list = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>(documented);
        names.addAll(used.keySet());
        for (String n : names) {
            list.add(new EnvVar(n, SECRET_NAME.matcher(n).find(), documented.contains(n), new ArrayList<>(used.getOrDefault(n, Set.of()))));
        }
        return list;
    }

    static Set<String> documented(Sources src) {
        Set<String> names = new LinkedHashSet<>();
        String example = src.get(".env.example");
        if (example != null) {
            Matcher m = Pattern.compile("(?m)^\\s*([A-Z][A-Z0-9_]*)\\s*=").matcher(example);
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    static Dependencies dependencies(Sources src, List<String> connectors, List<EnvVar> env) {
        String backendBuild = null;
        int packages = 0;
        int plugins = 0;
        String pom = src.get("backend/pom.xml");
        String gradle = src.get("backend/build.gradle.kts");
        String backendPackage = src.get("backend/package.json");
        String pyproject = src.get("backend/pyproject.toml");
        if (pom != null) {
            backendBuild = "Maven";
            packages = SpringReader.count(Pattern.compile("<dependency>"), pom);
            plugins = SpringReader.count(Pattern.compile("<plugin>"), pom);
        } else if (gradle != null) {
            backendBuild = "Gradle";
            packages = SpringReader.count(Pattern.compile("(?m)^\\s*(implementation|runtimeOnly|compileOnly|testImplementation|testRuntimeOnly|annotationProcessor|developmentOnly)\\("), gradle);
            plugins = SpringReader.count(Pattern.compile("(?m)^\\s*id\\(\"|^\\s*id\\s+\""), gradle);
        } else if (backendPackage != null) {
            backendBuild = "npm";
            packages = npmCount(backendPackage);
        } else if (pyproject != null) {
            backendBuild = "pip";
            String deps = Sources.first(Pattern.compile("(?s)dependencies\\s*=\\s*\\[(.*?)\\]"), pyproject, 1);
            packages = deps == null ? 0 : SpringReader.count(Pattern.compile("\"[^\"]+\""), deps);
        }
        String frontendPackage = src.get("frontend/package.json");
        return new Dependencies(backendBuild, packages, plugins, frontendPackage == null ? null : "npm",
                frontendPackage == null ? 0 : npmCount(frontendPackage), connectors, env);
    }

    private static int npmCount(String json) {
        int n = 0;
        for (String block : List.of("\"dependencies\"", "\"devDependencies\"")) {
            int at = json.indexOf(block);
            if (at < 0) {
                continue;
            }
            int open = json.indexOf('{', at);
            int close = json.indexOf('}', open);
            if (open > 0 && close > open) {
                n += SpringReader.count(Pattern.compile("\"[^\"]+\"\\s*:"), json.substring(open, close));
            }
        }
        return n;
    }
}
