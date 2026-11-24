package com.nova.constructor.project;

import com.nova.constructor.project.CommunityService.Card;
import com.nova.constructor.project.CommunityService.Comment;
import com.nova.constructor.project.CommunityService.Report;
import com.nova.constructor.project.CommunityService.ReportAction;
import com.nova.constructor.project.CommunityService.ReportReason;
import com.nova.constructor.project.NotificationService.Notification;
import com.nova.constructor.project.ProjectDtos.FileContent;
import com.nova.constructor.project.ProjectDtos.FileEntry;
import com.nova.constructor.project.ProjectDtos.ProjectSummary;
import com.nova.constructor.project.SharingService.CreatedLink;
import com.nova.constructor.project.SharingService.SharingState;
import com.nova.constructor.project.SharingService.SharingUpdate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Rede NOVA: compartilhamento do dono, descoberta, interação, links sem login, notificações e moderação. */
@RestController
@RequestMapping("/api/v1")
public class CommunityController {

    public record NewLink(Integer expiresInDays) {}

    public record NewComment(String body) {}

    public record NewReport(Long commentId, ReportReason reason, String details) {}

    public record Resolve(ReportAction action, String note) {}

    private final SharingService sharing;
    private final CommunityService community;
    private final NotificationService notifications;

    public CommunityController(SharingService sharing, CommunityService community, NotificationService notifications) {
        this.sharing = sharing;
        this.community = community;
        this.notifications = notifications;
    }

    // ---- compartilhamento (dono) ----

    @GetMapping("/projects/{id}/sharing")
    public SharingState sharing(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return sharing.get(id(jwt), id);
    }

    @PutMapping("/projects/{id}/sharing")
    public SharingState updateSharing(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody SharingUpdate body) {
        return sharing.update(id(jwt), id, body);
    }

    @PostMapping("/projects/{id}/sharing/links")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedLink createLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody(required = false) NewLink body) {
        return sharing.createLink(id(jwt), id, body == null ? null : body.expiresInDays());
    }

    @DeleteMapping("/projects/{id}/sharing/links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID linkId) {
        sharing.revokeLink(id(jwt), id, linkId);
    }

    // ---- rede (usuários logados) ----

    @GetMapping("/community/projects")
    public List<Card> explore(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String q,
                              @RequestParam(required = false) String tech, @RequestParam(defaultValue = "0") int page) {
        return community.explore(id(jwt), q, tech, page);
    }

    @GetMapping("/community/starred")
    public List<Card> starred(@AuthenticationPrincipal Jwt jwt) {
        return community.starred(id(jwt));
    }

    @GetMapping("/community/projects/{id}")
    public Card detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.detail(id(jwt), id);
    }

    @GetMapping("/community/projects/{id}/tree")
    public List<FileEntry> tree(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.tree(id(jwt), id);
    }

    @GetMapping("/community/projects/{id}/file")
    public FileContent file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam String path) {
        return community.file(id(jwt), id, path);
    }

    @GetMapping("/community/projects/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return attachment(community.download(id(jwt), id));
    }

    @PutMapping("/community/projects/{id}/star")
    public Card star(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.star(id(jwt), id, true);
    }

    @DeleteMapping("/community/projects/{id}/star")
    public Card unstar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.star(id(jwt), id, false);
    }

    @PostMapping("/community/projects/{id}/fork")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectSummary fork(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.fork(id(jwt), id);
    }

    @GetMapping("/community/projects/{id}/comments")
    public List<Comment> comments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return community.comments(id(jwt), id, admin(jwt));
    }

    @PostMapping("/community/projects/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public void comment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody NewComment body) {
        community.comment(id(jwt), id, body.body());
    }

    @DeleteMapping("/community/projects/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable long commentId) {
        community.deleteComment(id(jwt), id, commentId, admin(jwt));
    }

    @PostMapping("/community/projects/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody NewReport body) {
        community.report(id(jwt), id, body.commentId(), body.reason(), body.details());
    }

    // ---- link de compartilhamento (sem login) ----

    @GetMapping("/shared/{token}")
    public Card shared(@PathVariable String token) {
        return community.sharedDetail(token);
    }

    @GetMapping("/shared/{token}/tree")
    public List<FileEntry> sharedTree(@PathVariable String token) {
        return community.sharedTree(token);
    }

    @GetMapping("/shared/{token}/file")
    public FileContent sharedFile(@PathVariable String token, @RequestParam String path) {
        return community.sharedFile(token, path);
    }

    @GetMapping("/shared/{token}/download")
    public ResponseEntity<byte[]> sharedDownload(@PathVariable String token) {
        return attachment(community.sharedDownload(token));
    }

    // ---- notificações ----

    @GetMapping("/me/notifications")
    public Map<String, Object> notifications(@AuthenticationPrincipal Jwt jwt) {
        List<Notification> items = notifications.list(id(jwt));
        return Map.of("unread", notifications.unread(id(jwt)), "items", items);
    }

    @GetMapping("/me/notifications/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unread", notifications.unread(id(jwt)));
    }

    @PostMapping("/me/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long id) {
        notifications.markRead(id(jwt), id);
    }

    // ---- moderação (ADMIN) ----

    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Report> reports(@RequestParam(defaultValue = "OPEN") String status) {
        return community.reports(status);
    }

    @PostMapping("/admin/reports/{reportId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable long reportId, @RequestBody Resolve body) {
        community.resolve(id(jwt), reportId, body.action(), body.note());
    }

    private static ResponseEntity<byte[]> attachment(ProjectService.Archive archive) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archive.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archive.fileName() + "\"")
                .header("X-Checksum-Sha256", archive.sha256())
                .body(archive.bytes());
    }

    private static boolean admin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }

    private static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
