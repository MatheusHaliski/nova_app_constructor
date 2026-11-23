package com.nova.constructor.generator;

import com.nova.constructor.project.ProjectDtos.ProjectSummary;
import com.nova.constructor.project.ProjectService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Formulário de criação: pré-visualiza o plano e gera o projeto. */
@RestController
@RequestMapping("/api/v1/generator")
public class GeneratorController {

    private final GeneratorService generator;
    private final ProjectService projects;

    public GeneratorController(GeneratorService generator, ProjectService projects) {
        this.generator = generator;
        this.projects = projects;
    }

    /** Revisão antes de gerar: pacotes, plugins, variáveis, comandos de compilação e a lista de arquivos. */
    @PostMapping("/plan")
    public GenerationPlan plan(@Valid @RequestBody ProjectSpec spec) {
        return generator.generate(spec).plan();
    }

    /** Gera a estrutura inicial e salva como um novo projeto (privado) do usuário. */
    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectSummary create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectSpec spec) {
        return projects.createFromGenerated(UUID.fromString(jwt.getSubject()), generator.generate(spec));
    }
}
