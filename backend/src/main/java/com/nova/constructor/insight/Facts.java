package com.nova.constructor.insight;

import com.nova.constructor.insight.Model.Endpoint;
import com.nova.constructor.insight.Model.Entity;
import com.nova.constructor.insight.Model.Role;
import com.nova.constructor.insight.Model.Screen;
import com.nova.constructor.insight.Model.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fatos extraídos do código por cada leitor de stack, antes de montar a análise. */
final class Facts {

    record Item(Role role, String name, String path, String detail) {}

    /** Parâmetro de construtor de um controller Spring (para gerar testes com dublês). */
    record CtorParam(String type, String name) {}

    /**
     * Controller Spring.
     *
     * @param constructor parâmetros do construtor; {@code null} quando não dá para construir sem o Spring (injeção em campo)
     */
    record Controller(String name, String file, String pkg, String basePath, List<CtorParam> constructor, List<Endpoint> endpoints,
                      List<Endpoint> testableGets) {}

    final List<Item> items = new ArrayList<>();
    final List<Endpoint> endpoints = new ArrayList<>();
    final List<Entity> entities = new ArrayList<>();
    final List<Table> tables = new ArrayList<>();
    final List<Screen> screens = new ArrayList<>();
    final List<Controller> controllers = new ArrayList<>();
    final Set<String> testText = new HashSet<>();
    /** Nome da entidade → arquivos (fora a própria entidade) que alteram o campo de status. */
    final Map<String, Set<String>> statusWriters = new LinkedHashMap<>();
    int testCount;
    String basePackage;
    String basePackageDir;
    String securityFile;
    boolean securityPresent;
    String frontendFramework;

    boolean tested(String className) {
        return testText.stream().anyMatch(t -> t.matches("(?s).*\\b" + java.util.regex.Pattern.quote(className) + "\\b.*"));
    }
}
