package com.nova.constructor.project;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Detecta credenciais escritas em texto (RF-2.08): avisa na hora de salvar, antes que vazem em downloads. */
public final class SecretScanner {

    public record Finding(String path, int line, String kind) {}

    private record Rule(String kind, Pattern pattern) {}

    private static final List<Rule> RULES = List.of(
            new Rule("Chave privada", Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
            new Rule("Chave de acesso AWS", Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")),
            new Rule("Token do GitHub", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}\\b")),
            new Rule("Chave de API", Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}\\b")),
            new Rule("Senha ou segredo em texto", Pattern.compile(
                    "(?i)(password|passwd|senha|secret|token|api[_-]?key)\\w*\\s*[:=]\\s*[\"']?(?!\\$\\{)(?!process\\.env)(?!os\\.environ)[^\\s\"'${}<>]{8,}")));

    private SecretScanner() {}

    public static List<Finding> scan(String path, String content) {
        List<Finding> findings = new ArrayList<>();
        if (path.endsWith(".env.example") || path.endsWith(".md")) {
            return findings;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length && findings.size() < 20; i++) {
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(lines[i]).find()) {
                    findings.add(new Finding(path, i + 1, rule.kind()));
                    break;
                }
            }
        }
        return findings;
    }
}
