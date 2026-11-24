package com.nova.constructor.project;

import com.nova.constructor.project.ProjectDtos.ChangeResult;
import com.nova.constructor.project.ProjectDtos.ConfirmDelete;
import com.nova.constructor.project.ProjectDtos.DeletePath;
import com.nova.constructor.project.ProjectDtos.FileContent;
import com.nova.constructor.project.ProjectDtos.FileEntry;
import com.nova.constructor.project.ProjectDtos.LabelVersion;
import com.nova.constructor.project.ProjectDtos.MoveFile;
import com.nova.constructor.project.ProjectDtos.ProjectSummary;
import com.nova.constructor.project.ProjectDtos.Rename;
import com.nova.constructor.project.ProjectDtos.SaveFile;
import com.nova.constructor.project.ProjectDtos.Usage;
import com.nova.constructor.project.ProjectDtos.VersionSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Projetos do usuário autenticado: lista, arquivos, versões, ciclo de vida e download. */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectSummary> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "ACTIVE") Project.Status status) {
        return service.list(id(jwt), status);
    }

    @GetMapping("/usage")
    public Usage usage(@AuthenticationPrincipal Jwt jwt) {
        return service.usage(id(jwt));
    }

    @GetMapping("/{id}")
    public ProjectSummary get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(id(jwt), id);
    }

    @PatchMapping("/{id}")
    public ProjectSummary rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Rename body) {
        return service.rename(id(jwt), id, body.name(), body.description());
    }

    @GetMapping("/{id}/tree")
    public List<FileEntry> tree(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) Integer version) {
        return service.tree(id(jwt), id, version);
    }

    @GetMapping("/{id}/file")
    public FileContent file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam String path,
                            @RequestParam(required = false) Integer version) {
        return service.file(id(jwt), id, path, version);
    }

    @PutMapping("/{id}/file")
    public ChangeResult save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SaveFile body) {
        return service.saveFile(id(jwt), id, body.path(), body.content(), body.baseVersion(), Boolean.TRUE.equals(body.createOnly()));
    }

    @PostMapping("/{id}/files/move")
    public ChangeResult move(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody MoveFile body) {
        return service.move(id(jwt), id, body.from(), body.to(), body.baseVersion());
    }

    @PostMapping("/{id}/files/delete")
    public ChangeResult deletePath(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody DeletePath body) {
        return service.delete(id(jwt), id, body.path(), body.baseVersion());
    }

    @GetMapping("/{id}/versions")
    public List<VersionSummary> versions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.versions(id(jwt), id);
    }

    @PutMapping("/{id}/versions/{number}/label")
    public VersionSummary label(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int number,
                                @Valid @RequestBody LabelVersion body) {
        return service.labelVersion(id(jwt), id, number, body.label());
    }

    @PostMapping("/{id}/versions/{number}/restore")
    public ChangeResult restore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int number) {
        return service.restore(id(jwt), id, number);
    }

    @PostMapping("/{id}/archive")
    public ProjectSummary archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.setStatus(id(jwt), id, Project.Status.ARCHIVED);
    }

    /** Volta um projeto arquivado ou da lixeira para ativo. */
    @PostMapping("/{id}/activate")
    public ProjectSummary activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.setStatus(id(jwt), id, Project.Status.ACTIVE);
    }

    /** Move para a lixeira (recuperável). */
    @DeleteMapping("/{id}")
    public ProjectSummary trash(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.setStatus(id(jwt), id, Project.Status.TRASHED);
    }

    @PostMapping("/{id}/delete-permanently")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePermanently(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ConfirmDelete body) {
        service.deletePermanently(id(jwt), id, body.confirmName());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @RequestParam(required = false) Integer version,
                                           @RequestParam(required = false) String path,
                                           @RequestParam(defaultValue = "zip") String format) {
        ProjectService.Archive archive = service.download(id(jwt), id, version, path, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archive.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archive.fileName() + "\"")
                .header("X-Checksum-Sha256", archive.sha256())
                .body(archive.bytes());
    }

    private static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
