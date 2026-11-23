package com.nova.constructor.project;

import com.nova.constructor.common.ApiException;
import org.springframework.http.HttpStatus;

/** Regras de caminho dos arquivos de um projeto: sem sair da raiz, sem caracteres de controle. */
final class PathRules {

    private PathRules() {}

    static String normalize(String raw) {
        if (raw == null) {
            throw invalid("Informe o caminho.");
        }
        String path = raw.trim().replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty() || path.length() > 400) {
            throw invalid("Caminho vazio ou longo demais.");
        }
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw invalid("Caminho inválido: " + raw);
            }
            if (part.chars().anyMatch(c -> c < 32 || c == 127 || "<>:\"|?*".indexOf(c) >= 0)) {
                throw invalid("O caminho tem caracteres não permitidos: " + raw);
            }
        }
        if (path.equals(".git") || path.startsWith(".git/")) {
            throw invalid("A pasta .git não pode ser alterada.");
        }
        return path;
    }

    /** Arquivos de segredos que nunca entram em downloads: .env e variantes (menos .env.example). */
    static boolean isSecretFile(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return (name.equals(".env") || name.startsWith(".env.")) && !name.equals(".env.example");
    }

    static boolean isExecutable(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.equals("mvnw") || name.equals("gradlew") || name.endsWith(".sh");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
