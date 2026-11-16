package com.cloudfs.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.cloudfs.service.ArchiveService;
import com.cloudfs.service.FolderSharingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
public class ArchiveController {
    public record ArchiveRequest(@NotNull @Size(max = 1000) List<@NotNull UUID> fileIds,
                                 @NotNull @Size(max = 1000) List<@NotNull UUID> folderIds) { }
    private final ArchiveService archives;
    private final FolderSharingService sharing;
    public ArchiveController(ArchiveService archives, FolderSharingService sharing) { this.archives = archives; this.sharing = sharing; }

    @PostMapping("/api/downloads/zip")
    public ResponseEntity<StreamingResponseBody> download(@Valid @RequestBody ArchiveRequest request, Authentication auth) {
        var entries = archives.plan(auth.getName(), request.fileIds(), request.folderIds());
        return response(archives, entries, "Nimbus Drive.zip");
    }

    @GetMapping("/api/folder-invitations/{id}/zip")
    public ResponseEntity<StreamingResponseBody> shared(@PathVariable UUID id, @RequestParam(required = false) UUID folderId, Authentication auth) {
        var folder = sharing.invitationFolder(auth.getName(), id, folderId);
        return response(archives, archives.planFolder(folder), folder.getName() + ".zip");
    }

    @GetMapping("/api/public/folders/{token}/zip")
    public ResponseEntity<StreamingResponseBody> publicShared(@PathVariable String token, @RequestParam(required = false) UUID folderId) {
        var folder = sharing.linkFolder(token, folderId);
        return response(archives, archives.planFolder(folder), folder.getName() + ".zip");
    }

    static ResponseEntity<StreamingResponseBody> response(ArchiveService archives, List<ArchiveService.Entry> entries, String name) {
        StreamingResponseBody body = output -> archives.write(entries, output);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(body);
    }
}
