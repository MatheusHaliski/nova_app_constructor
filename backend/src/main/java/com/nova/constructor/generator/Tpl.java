package com.nova.constructor.generator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

/** Utilitários dos geradores: substituição de marcadores {{nome}} e leitura de modelos do classpath. */
final class Tpl {

    private Tpl() {}

    private static final Pattern MARKER = Pattern.compile("\\{\\{(\\w+)}}");

    /** Substitui {{nome}} em uma única passada; valores não são reprocessados e marcadores desconhecidos ficam como estão. */
    static String fill(String template, Map<String, String> values) {
        Matcher m = MARKER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "Minha Loja" → "MinhaLoja". */
    static String pascal(String slug) {
        StringBuilder sb = new StringBuilder();
        for (String part : slug.split("-")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
