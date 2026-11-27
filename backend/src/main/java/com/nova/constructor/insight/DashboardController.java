package com.nova.constructor.insight;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Painel inicial da conta. */
@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/dashboard")
    public DashboardService.Dashboard dashboard(@AuthenticationPrincipal Jwt jwt) {
        return service.dashboard(UUID.fromString(jwt.getSubject()));
    }
}
