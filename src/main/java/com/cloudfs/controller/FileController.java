package com.cloudfs.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.dto.Dtos.CreateShareRequest;
import com.cloudfs.dto.Dtos.FileDto;
import com.cloudfs.dto.Dtos.MoveRequest;
import com.cloudfs.dto.Dtos.NameRequest;
import com.cloudfs.dto.Dtos.PresignDto;
import com.cloudfs.dto.Dtos.ShareDto;
import com.cloudfs.dto.Dtos.StarRequest;
import com.cloudfs.model.FileMeta;
import com.cloudfs.service.FileService;
import com.cloudfs.service.ShareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Files")
@SecurityRequirement(name = "bearer")
public class FileController {

    private final FileService files;
    private final ShareService shares;

    public FileController(FileService files, ShareService shares) {
        this.files = files;
        this.shares = shares;
    }

    @Operation(summary = "Upload 1 file (multipart, field `file`)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileDto upload(@RequestParam("file") MultipartFile file,
                          @RequestParam(required = false) UUID folderId,
                          Authentication auth) throws IOException {
        return FileDto.of(files.upload(file, auth.getName(), folderId));
    }

    @Operation(summary = "List files",
            description = "Default: files in folder `folderId` (empty = root). "
                    + "`q` = search by name across all files, `view=starred|recent|all`.")
    @GetMapping
    public List<FileDto> list(@RequestParam(required = false) UUID folderId,
                              @RequestParam(required = false) String q,
                              @Parameter(description = "starred | recent | all")
                              @RequestParam(required = false) String view,
                              Authentication auth) {
        String owner = auth.getName();
        List<FileMeta> result;
        if (q != null && !q.isBlank()) {
            result = files.search(owner, q);
        } else if ("starred".equals(view)) {
            result = files.starred(owner);
        } else if ("recent".equals(view)) {
            result = files.recent(owner);
        } else if ("all".equals(view)) {
            result = files.all(owner);
        } else {
            result = files.list(owner, folderId);
        }
        return result.stream().map(FileDto::of).toList();
    }

    @GetMapping("/{id}")
    public FileDto get(@PathVariable UUID id, Authentication auth) {
        return FileDto.of(files.findOwned(id, auth.getName()));
    }

    @Operation(summary = "Download a file (streamed through the server)",
            description = "`inline=true` to preview in the browser; only applies to images/PDF/text/video/audio.")
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id,
                                                        @RequestParam(defaultValue = "false") boolean inline,
                                                        Authentication auth) throws IOException {
        FileMeta meta = files.findOwned(id, auth.getName());
        return stream(files, meta, inline);
    }

    /** Dung chung cho tai file cua minh va tai qua link chia se. */
    static ResponseEntity<InputStreamResource> stream(FileService files, FileMeta meta, boolean inline)
            throws IOException {
        boolean showInline = inline && files.inlineSafe(meta);
        String type = meta.getContentType() != null ? meta.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        ContentDisposition disposition = (showInline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(meta.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(showInline ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(meta.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Noi dung do nguoi dung tai len: khong cho chay script ke ca khi bi mo truc tiep
                .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new InputStreamResource(files.open(meta)));
    }

    @Operation(summary = "Create a presigned URL to download directly from S3 (expires after 15 minutes)")
    @GetMapping("/{id}/presign")
    public PresignDto presign(@PathVariable UUID id, Authentication auth) {
        FileMeta meta = files.findOwned(id, auth.getName());
        return files.presign(meta)
                .map(url -> new PresignDto(url.toString(), Instant.now().plus(files.presignTtl())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                        "The current storage backend does not support presigned URLs"));
    }

    @Operation(summary = "Rename a file")
    @PatchMapping("/{id}")
    public FileDto rename(@PathVariable UUID id, @Valid @RequestBody NameRequest body, Authentication auth) {
        return FileDto.of(files.rename(auth.getName(), id, body.name()));
    }

    @Operation(summary = "Move a file to another folder (folderId=null: to root)")
    @PostMapping("/{id}/move")
    public FileDto move(@PathVariable UUID id, @RequestBody MoveRequest body, Authentication auth) {
        return FileDto.of(files.move(auth.getName(), id, body.folderId()));
    }

    @Operation(summary = "Star / unstar")
    @PutMapping("/{id}/star")
    public FileDto star(@PathVariable UUID id, @RequestBody StarRequest body, Authentication auth) {
        return FileDto.of(files.star(auth.getName(), id, body.starred()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        files.delete(auth.getName(), id);
    }

    @Operation(summary = "Create a public share link")
    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareDto share(@PathVariable UUID id, @Valid @RequestBody(required = false) CreateShareRequest body,
                          Authentication auth) {
        Integer hours = body == null ? null : body.expiresInHours();
        var link = shares.create(auth.getName(), id, hours);
        return ShareDto.of(link, files.findOwned(id, auth.getName()).getOriginalName());
    }
}
