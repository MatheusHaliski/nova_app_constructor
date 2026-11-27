package com.nova.constructor.insight;

import com.nova.constructor.insight.InsightsService.ImprovementRequest;
import com.nova.constructor.insight.Model.ApplyResult;
import com.nova.constructor.insight.Model.Insights;
import com.nova.constructor.insight.Model.Preview;
import com.nova.constructor.insight.Model.PreviewBundle;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Visualizador do projeto: análise da versão, aba Melhorias (prévia, aplicação e dispensa) e material da prévia das telas. */
@RestController
@RequestMapping("/api/v1/projects/{id}")
public class InsightsController {

    private final InsightsService service;

    public InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping("/insights")
    public Insights insights(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) Integer version) {
        return service.insights(owner(jwt), id, version);
    }

    @PostMapping("/improvements/{proposalId}/preview")
    public Preview preview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String proposalId,
                           @RequestBody ImprovementRequest body) {
        return service.preview(owner(jwt), id, proposalId, body);
    }

    @PostMapping("/improvements/{proposalId}/apply")
    public ApplyResult apply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String proposalId,
                             @RequestBody ImprovementRequest body) {
        return service.apply(owner(jwt), id, proposalId, body);
    }

    @PutMapping("/improvements/{proposalId}/dismissal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String proposalId) {
        service.dismiss(owner(jwt), id, proposalId, true);
    }

    @DeleteMapping("/improvements/{proposalId}/dismissal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String proposalId) {
        service.dismiss(owner(jwt), id, proposalId, false);
    }

    @GetMapping("/preview-bundle")
    public PreviewBundle previewBundle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) Integer version) {
        return service.previewBundle(owner(jwt), id, version);
    }

    private static UUID owner(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
