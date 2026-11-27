package com.nova.constructor.insight;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Conversões de nomes (telas, recursos, entidades) entre os formatos usados no código. */
public final class Names {

    private Names() {}

    public static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Minúsculas, sem acentos, só letras e números: chave para comparar nomes. */
    public static String normalize(String s) {
        return stripAccents(s == null ? "" : s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Singular aproximado do português (e do inglês) para casar "Produtos" com "Produto". */
    public static String singular(String word) {
        String w = word;
        if (w.length() <= 3) {
            return w;
        }
        if (w.endsWith("oes") || w.endsWith("aes")) {
            return w.substring(0, w.length() - 3) + "ao";
        }
        if (w.endsWith("ns")) {
            return w.substring(0, w.length() - 2) + "m";
        }
        if (w.endsWith("ais") || w.endsWith("eis") || w.endsWith("ois")) {
            return w.substring(0, w.length() - 2) + "l";
        }
        if (w.endsWith("res") || w.endsWith("zes") || w.endsWith("ses")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    /** Chave para casar nomes no singular ou no plural. */
    public static String key(String s) {
        return singular(normalize(s));
    }

    public static String[] words(String s) {
        String spaced = stripAccents(s).replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Arrays.stream(spaced.split("[^A-Za-z0-9]+")).filter(w -> !w.isEmpty()).toArray(String[]::new);
    }

    public static String pascal(String s) {
        String joined = Arrays.stream(words(s)).map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining());
        if (joined.isEmpty()) {
            return "Item";
        }
        return Character.isDigit(joined.charAt(0)) ? "X" + joined : joined;
    }

    public static String camel(String s) {
        String p = pascal(s);
        return Character.toLowerCase(p.charAt(0)) + p.substring(1);
    }

    public static String kebab(String s) {
        return Arrays.stream(words(s)).map(w -> w.toLowerCase(Locale.ROOT)).collect(Collectors.joining("-"));
    }

    public static String snake(String s) {
        return Arrays.stream(words(s)).map(w -> w.toLowerCase(Locale.ROOT)).collect(Collectors.joining("_"));
    }

    public static String constant(String s) {
        return snake(s).toUpperCase(Locale.ROOT);
    }

    /** Plural simples, para o caminho da API a partir do nome da entidade. */
    public static String plural(String kebab) {
        if (kebab.endsWith("s")) {
            return kebab;
        }
        if (kebab.endsWith("ao")) {
            return kebab.substring(0, kebab.length() - 2) + "oes";
        }
        if (kebab.endsWith("r") || kebab.endsWith("z")) {
            return kebab + "es";
        }
        if (kebab.endsWith("m")) {
            return kebab.substring(0, kebab.length() - 1) + "ns";
        }
        if (kebab.endsWith("al") || kebab.endsWith("el") || kebab.endsWith("ol") || kebab.endsWith("ul")) {
            return kebab.substring(0, kebab.length() - 1) + "is";
        }
        return kebab + "s";
    }

    public static boolean isJavaIdentifier(String s) {
        return s != null && s.matches("[A-Za-z_][A-Za-z0-9_]*") && !JAVA_KEYWORDS.contains(s);
    }

    private static final java.util.Set<String> JAVA_KEYWORDS = java.util.Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
            "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "record", "var", "yield");
}
