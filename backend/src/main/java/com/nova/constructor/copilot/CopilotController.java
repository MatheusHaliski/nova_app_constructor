package com.nova.constructor.copilot;

import com.nova.constructor.copilot.CopilotDtos.AssistRequest;
import com.nova.constructor.copilot.CopilotDtos.AssistResponse;
import com.nova.constructor.copilot.CopilotDtos.Proposal;
import com.nova.constructor.copilot.CopilotDtos.ProposeRequest;
import com.nova.constructor.copilot.CopilotDtos.Status;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Copilot do formulário: proposta completa (modo Copilot total) e ajuda por campo (modo assistido). */
@RestController
@RequestMapping("/api/v1/copilot")
public class CopilotController {

    private final CopilotService copilot;

    public CopilotController(CopilotService copilot) {
        this.copilot = copilot;
    }

    @GetMapping("/status")
    public Status status() {
        return copilot.status();
    }

    @PostMapping("/propose")
    public Proposal propose(@Valid @RequestBody ProposeRequest request) {
        return copilot.propose(request.idea(), request.type());
    }

    @PostMapping("/assist")
    public AssistResponse assist(@Valid @RequestBody AssistRequest request) {
        return copilot.assist(request.field(), request.idea(), request.name(), request.current());
    }

    /** Começar de um esboço: foto de desenho ou print vira telas e dados. */
    @PostMapping(path = "/sketch", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public CopilotService.SketchResult sketch(@org.springframework.web.bind.annotation.RequestParam("image") org.springframework.web.multipart.MultipartFile image,
                                              @org.springframework.web.bind.annotation.RequestParam(value = "hint", required = false) String hint) throws java.io.IOException {
        return copilot.sketch(image.getBytes(), image.getContentType(), hint);
    }
}
