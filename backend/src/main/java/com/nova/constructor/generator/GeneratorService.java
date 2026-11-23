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
