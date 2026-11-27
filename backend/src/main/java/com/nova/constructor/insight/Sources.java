package com.nova.constructor.insight;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Arquivos de texto de uma versão do projeto, com utilidades de busca. */
final class Sources {

    private final Map<String, String> files;

    Sources(Map<String, String> files) {
        this.files = Collections.unmodifiableMap(new TreeMap<>(files));
    }

    Map<String, String> all() {
        return files;
    }

    String get(String path) {
        return files.get(path);
    }

    boolean has(String path) {
        return files.containsKey(path);
    }

    List<String> paths(String regex) {
        Pattern p = Pattern.compile(regex);
        return files.keySet().stream().filter(k -> p.matcher(k).matches()).toList();
    }

    boolean any(String regex) {
        return !paths(regex).isEmpty();
    }

    static int line(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    static String first(Pattern pattern, String text, int group) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(group) : null;
    }

    static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
