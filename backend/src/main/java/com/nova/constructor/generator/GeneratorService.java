package com.nova.constructor.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Orquestra a geração: plano → arquivos do backend, do frontend e da raiz. */
@Service
public class GeneratorService {

    /** Resultado da geração: o plano (com a lista de arquivos) e o conteúdo de cada arquivo. */
    public record Generated(GenerationPlan plan, Map<String, String> files) {}

    private final PlanService planService;
    private final Map<String, StackGenerator> generators;
    private final CommonFilesGenerator common;

    public GeneratorService(PlanService planService, List<StackGenerator> generators, CommonFilesGenerator common) {
        this.planService = planService;
        this.generators = generators.stream().collect(Collectors.toMap(StackGenerator::id, Function.identity()));
        this.common = common;
    }

    /** Uma stack de backend na comparação: o que o gerador produziria com a mesma ideia. */
    public record StackComparison(String backend, String name, String language, String buildTool, int backendFiles, List<String> folders,
                                  int dependencies, int plugins, String build, String run, String test, boolean entitiesAsCode, String requires,
                                  List<String> warnings, String error) {}

    /** Gera a mesma especificação em cada backend disponível, sem salvar, para comparar lado a lado. */
    public List<StackComparison> compare(ProjectSpec spec, List<String> backendIds) {
        List<StackComparison> out = new ArrayList<>();
        for (String id : backendIds) {
            ProjectSpec variant = spec.withBackend(new ProjectSpec.Backend(id, null, null, null, null, null));
            try {
                Generated g = generate(variant);
                Map<String, String> files = g.files();
                List<String> backendFiles = files.keySet().stream().filter(p -> p.startsWith("backend/")).toList();
                List<String> folders = backendFiles.stream().map(p -> p.substring("backend/".length()))
                        .map(p -> p.contains("/") ? p.substring(0, p.indexOf('/')) + "/" : p).distinct().sorted().toList();
                GenerationPlan.Side b = g.plan().backend();
                String requires = switch (b.language().toLowerCase(java.util.Locale.ROOT)) {
                    case "java", "kotlin" -> "JDK " + b.languageVersion();
                    case "typescript", "javascript" -> "Node.js 22";
                    case "python" -> "Python " + b.languageVersion();
                    default -> b.language() + " " + b.languageVersion();
                };
                out.add(new StackComparison(id, b.name(), b.language() + " " + b.languageVersion(), b.commands().tool(), backendFiles.size(), folders,
                        b.dependencies().size(), b.plugins().size(), b.commands().build(), b.commands().run(), b.commands().test(),
                        "spring-boot".equals(id) && !g.plan().spec().entitiesOrEmpty().isEmpty(), requires, g.plan().warnings(), null));
            } catch (com.nova.constructor.common.ApiException e) {
                out.add(new StackComparison(id, id, null, null, 0, List.of(), 0, 0, null, null, null, false, null, List.of(), e.getMessage()));
            }
        }
        return out;
    }

    public Generated generate(ProjectSpec spec) {
        GenerationPlan plan = planService.plan(spec);
        Map<String, String> files = new TreeMap<>();
        generators.get(plan.backend().id()).generate(plan, "backend/", files);
        if (plan.frontend() != null) {
            generators.get(plan.frontend().id()).generate(plan, "frontend/", files);
        }
        common.generate(plan, files);
        GenerationPlan withFiles = new GenerationPlan(plan.spec(), plan.slug(), plan.backend(), plan.frontend(), plan.env(),
                new ArrayList<>(files.keySet()), plan.warnings());
        return new Generated(withFiles, files);
    }
}
