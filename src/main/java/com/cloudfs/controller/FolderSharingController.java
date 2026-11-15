package com.cloudfs.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.cloudfs.dto.Dtos.CreateShareRequest;
import com.cloudfs.service.FileService;
import com.cloudfs.service.FolderSharingService;
import com.cloudfs.service.FolderSharingService.Contents;
import com.cloudfs.service.FolderSharingService.Invitation;
import com.cloudfs.service.FolderSharingService.Link;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class FolderSharingController {
    public record InviteRequest(@NotBlank @Size(max = 50) String username) { }
    private final FolderSharingService sharing;
    private final FileService files;

    public FolderSharingController(FolderSharingService sharing, FileService files) {
        this.sharing = sharing;
        this.files = files;
    }

    @PostMapping("/api/folders/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public Invitation invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest body, Authentication auth) {
        return sharing.invite(auth.getName(), id, body.username().strip());
    }
    @GetMapping("/api/folders/{id}/invitations")
    public List<Invitation> sent(@PathVariable UUID id, Authentication auth) { return sharing.sent(auth.getName(), id); }
    @GetMapping("/api/folder-invitations")
    public List<Invitation> incoming(Authentication auth) { return sharing.incoming(auth.getName()); }
    @PostMapping("/api/folder-invitations/{id}/accept")
    public void accept(@PathVariable UUID id, Authentication auth) { sharing.accept(auth.getName(), id); }
    @PostMapping("/api/folder-invitations/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable UUID id, Authentication auth) { sharing.decline(auth.getName(), id); }
    @DeleteMapping("/api/folder-invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable UUID id, Authentication auth) { sharing.revokeInvitation(auth.getName(), id); }
    @GetMapping("/api/folder-invitations/{id}/contents")
    public Contents invitationContents(@PathVariable UUID id, @RequestParam(required = false) UUID folderId, Authentication auth) {
        return sharing.invitationContents(auth.getName(), id, folderId);
    }
    @GetMapping("/api/folder-invitations/{id}/files/{fileId}")
    public ResponseEntity<InputStreamResource> invitationFile(@PathVariable UUID id, @PathVariable UUID fileId,
            @RequestParam(defaultValue = "false") boolean inline, Authentication auth) throws IOException {
        return FileController.stream(files, sharing.invitationFile(auth.getName(), id, fileId), inline);
    }

    @PostMapping("/api/folders/{id}/links")
    @ResponseStatus(HttpStatus.CREATED)
    public Link createLink(@PathVariable UUID id, @Valid @RequestBody CreateShareRequest body, Authentication auth) {
        return sharing.createLink(auth.getName(), id, body.expiresInHours());
    }
    @GetMapping("/api/folders/{id}/links")
    public List<Link> links(@PathVariable UUID id, Authentication auth) { return sharing.links(auth.getName(), id); }
    @DeleteMapping("/api/folder-links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeLink(@PathVariable UUID id, Authentication auth) { sharing.revokeLink(auth.getName(), id); }
    @GetMapping("/api/public/folders/{token}")
    public Contents publicContents(@PathVariable String token, @RequestParam(required = false) UUID folderId) {
        return sharing.linkContents(token, folderId);
    }
    @GetMapping("/api/public/folders/{token}/files/{fileId}")
    public ResponseEntity<InputStreamResource> publicFile(@PathVariable String token, @PathVariable UUID fileId,
            @RequestParam(defaultValue = "false") boolean inline) throws IOException {
        return FileController.stream(files, sharing.linkFile(token, fileId), inline);
    }
}
