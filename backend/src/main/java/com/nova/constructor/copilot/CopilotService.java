package com.nova.constructor.copilot;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.nova.constructor.catalog.Catalog;
import com.nova.constructor.catalog.CatalogService;
import com.nova.constructor.common.ApiException;
import com.nova.constructor.config.NovaProperties;
import com.nova.constructor.copilot.CopilotDtos.AssistResponse;
import com.nova.constructor.copilot.CopilotDtos.BacklogItem;
import com.nova.constructor.copilot.CopilotDtos.Proposal;
import com.nova.constructor.copilot.CopilotDtos.Status;
import com.nova.constructor.generator.PlanService;
import com.nova.constructor.generator.ProjectSpec;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Copilot do formulário. Usa o Claude quando há chave configurada; em qualquer falha ou recusa cai para as regras
 * simples e avisa o usuário, para o formulário nunca travar. A proposta sempre passa pela validação do catálogo.
 */
@Service
public class CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);
    private static final String AI_UNAVAILABLE = "A IA não respondeu agora; esta sugestão veio das regras simples do NOVA.";
    private static final String AI_NOT_CONFIGURED = "A IA do NOVA não está configurada neste ambiente; esta sugestão veio de regras simples.";

    private final ClaudeCopilot claude;
    private final RuleBasedCopilot rules;
    private final CatalogService catalog;
    private final PlanService planService;

    public CopilotService(NovaProperties props, RuleBasedCopilot rules, CatalogService catalog, PlanService planService) {
        String key = props.ai().anthropicApiKey();
        this.claude = key == null || key.isBlank() ? null : new ClaudeCopilot(key, props.ai().model());
        this.rules = rules;
        this.catalog = catalog;
        this.planService = planService;
    }

    public Status status() {
        return claude == null ? new Status("regras", null) : new Status("claude", claude.model());
    }

    public Proposal propose(String idea, String type) {
        if (claude != null) {
            try {
                Optional<CopilotDraft> draft = claude.propose(idea, type, catalog.get());
                if (draft.isPresent()) {
                    Optional<Proposal> proposal = toProposal(draft.get(), "claude", null);
                    if (proposal.isPresent()) {
                        return proposal.get();
                    }
                }
            } catch (RateLimitException e) {
                log.warn("Copilot: limite de requisições da IA atingido.");
            } catch (AnthropicServiceException e) {
                log.warn("Copilot: a API respondeu {}.", e.statusCode());
            } catch (AnthropicException e) {
                log.warn("Copilot: falha ao chamar a IA: {}", e.getMessage());
            }
        }
        return toProposal(rules.propose(idea, type), "regras", claude == null ? AI_NOT_CONFIGURED : AI_UNAVAILABLE).orElseThrow();
    }

    public AssistResponse assist(String field, String idea, String name, String current) {
        if (claude != null) {
            try {
                Optional<String> text = claude.assist(field, idea, name, current);
                if (text.isPresent()) {
                    return new AssistResponse(text.get(), "claude", null);
                }
            } catch (RateLimitException e) {
                log.warn("Copilot: limite de requisições da IA atingido.");
            } catch (AnthropicServiceException e) {
                log.warn("Copilot: a API respondeu {}.", e.statusCode());
            } catch (AnthropicException e) {
                log.warn("Copilot: falha ao chamar a IA: {}", e.getMessage());
            }
        }
        return new AssistResponse(rules.assist(field, idea, name), "regras", claude == null ? AI_NOT_CONFIGURED : AI_UNAVAILABLE);
    }

    /** Converte o rascunho em especificação e confere contra o catálogo; vazio se a IA escolheu algo inválido. */
    private Optional<Proposal> toProposal(CopilotDraft draft, String source, String notice) {
        Catalog c = catalog.get();
        String frontend = blank(draft.frontendId()) ? null : draft.frontendId();
        String database = blank(draft.databaseId()) ? null : draft.databaseId();
        List<String> screens = draft.screens() == null ? List.of() : draft.screens().stream().limit(30).map(s -> cut(s, 60)).toList();
        boolean auth = draft.features() != null && draft.features().contains("auth");
        DomainSuggestions.Result domain = domainOf(draft, screens, auth);
        ProjectSpec spec = new ProjectSpec(
                cut(blank(draft.name()) ? "Meu App" : draft.name(), 80), cut(draft.description(), 500), cut(draft.objective(), 4000),
                "APP".equals(draft.type()) ? "APP" : "WEB_APP", "COPILOT",
                new ProjectSpec.Backend(draft.backendId(), null, null, null, null, null),
                frontend == null ? null : new ProjectSpec.Frontend(frontend, null, null),
                database == null ? null : new ProjectSpec.Database(database, null),
                draft.connectors() == null ? List.of() : draft.connectors(),
                draft.usesAi() ? new ProjectSpec.Ai(c.aiProviders().stream().filter(Catalog.AiProvider::available).findFirst().orElseThrow().id(), null) : null,
                draft.features() == null ? null : draft.features(),
                screens, domain.entities(), domain.screens(), domain.requirements(), null);
        try {
            ProjectSpec normalized = planService.plan(spec).spec();
            List<BacklogItem> backlog = draft.backlog() == null ? List.of()
                    : draft.backlog().stream().map(s -> new BacklogItem(s.title(), s.story())).toList();
            return Optional.of(new Proposal(normalized, draft.requirements() == null ? List.of() : draft.requirements(), backlog,
                    draft.rationale(), source, notice));
        } catch (ApiException e) {
            log.warn("Copilot: proposta inválida para o catálogo: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Entidades e telas da IA quando vieram completas; senão, deduzidas dos nomes das telas. Requisitos da IA vêm antes. */
    private static DomainSuggestions.Result domainOf(CopilotDraft draft, List<String> screens, boolean auth) {
        DomainSuggestions.Result rules = DomainSuggestions.fromScreens(screens, auth);
        List<ProjectSpec.EntitySpec> entities = rules.entities();
        List<ProjectSpec.ScreenSpec> details = rules.screens();
        if (draft.entities() != null && !draft.entities().isEmpty()) {
            entities = draft.entities().stream().limit(8).map(e -> new ProjectSpec.EntitySpec(cut(e.name(), 40), e.fields() == null ? List.of()
                    : e.fields().stream().limit(10).map(f -> new ProjectSpec.FieldSpec(cut(f.name(), 40), f.type(), f.required(),
                            f.values() == null || f.values().isEmpty() ? null : f.values())).toList())).toList();
            details = draft.screenDetails() == null ? List.of() : draft.screenDetails().stream().filter(s -> s.name() != null)
                    .map(s -> new ProjectSpec.ScreenSpec(cut(s.name(), 60), s.template(), blank(s.entity()) ? null : s.entity(), s.access(), null, null)).toList();
        }
        List<ProjectSpec.Requirement> requirements = new java.util.ArrayList<>();
        if (draft.requirements() != null) {
            draft.requirements().stream().limit(30).forEach(r -> requirements.add(new ProjectSpec.Requirement(cut(r, 300), null, "IMPORTANT")));
        }
        rules.requirements().forEach(r -> {
            if (requirements.size() < 50) {
                requirements.add(r);
            }
        });
        return new DomainSuggestions.Result(entities, details, requirements);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("nenhum");
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
