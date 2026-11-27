package com.nova.constructor.insight;

import java.util.ArrayList;
import java.util.List;

/** Diferença linha a linha (LCS) em formato unificado, com limites para arquivos grandes. */
final class Diff {

    private static final int MAX_LINES = 2500;
    private static final int MAX_OUTPUT = 400;

    private Diff() {}

    record Result(int added, int removed, String text) {}

    static Result of(String before, String after) {
        if (before == null && after == null) {
            return new Result(0, 0, "(arquivo binário)");
        }
        String[] a = before == null ? new String[0] : before.split("\n", -1);
        String[] b = after == null ? new String[0] : after.split("\n", -1);
        if (a.length > MAX_LINES || b.length > MAX_LINES) {
            return new Result(b.length, a.length, "(arquivo grande demais para mostrar a diferença)");
        }
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<String> ops = new ArrayList<>();
        int i = 0;
        int j = 0;
        int added = 0;
        int removed = 0;
        while (i < a.length || j < b.length) {
            if (i < a.length && j < b.length && a[i].equals(b[j])) {
                ops.add("  " + a[i]);
                i++;
                j++;
            } else if (j < b.length && (i >= a.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                ops.add("+ " + b[j++]);
                added++;
            } else {
                ops.add("- " + a[i++]);
                removed++;
            }
        }
        StringBuilder out = new StringBuilder();
        int shown = 0;
        int lastPrinted = -10;
        for (int k = 0; k < ops.size() && shown < MAX_OUTPUT; k++) {
            boolean near = false;
            for (int d = -2; d <= 2; d++) {
                int x = k + d;
                if (x >= 0 && x < ops.size() && !ops.get(x).startsWith("  ")) {
                    near = true;
                    break;
                }
            }
            if (near) {
                if (k - lastPrinted > 1 && shown > 0) {
                    out.append("…\n");
                }
                out.append(ops.get(k)).append('\n');
                lastPrinted = k;
                shown++;
            }
        }
        return new Result(added, removed, out.toString());
    }
}
