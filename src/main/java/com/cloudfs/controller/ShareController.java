package com.cloudfs.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cloudfs.dto.Dtos.PublicShareDto;
import com.cloudfs.dto.Dtos.ShareDto;
import com.cloudfs.model.FileMeta;
import com.cloudfs.service.FileCategory;
import com.cloudfs.service.FileService;
import com.cloudfs.service.ShareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Sharing")
public class ShareController {

    private final ShareService shares;
    private final FileService files;

    public ShareController(ShareService shares, FileService files) {
        this.shares = shares;
        this.files = files;
    }

    @Operation(summary = "Các link chia sẻ tôi đã tạo")
    @SecurityRequirement(name = "bearer")
    @GetMapping("/api/shares")
    public List<ShareDto> mine(Authentication auth) {
        return shares.list(auth.getName()).stream().map(s -> ShareDto.of(s, shares.fileName(s))).toList();
    }

    @Operation(summary = "Thu hồi link chia sẻ")
    @SecurityRequirement(name = "bearer")
    @DeleteMapping("/api/shares/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, Authentication auth) {
        shares.revoke(auth.getName(), id);
    }

    @Operation(summary = "[Công khai] Thông tin file được chia sẻ")
    @GetMapping("/api/public/shares/{token}")
    public PublicShareDto info(@PathVariable String token) {
        var link = shares.resolve(token);
        FileMeta f = shares.fileOf(link);
        return new PublicShareDto(f.getOriginalName(), f.getContentType(), f.getSize(), f.getOwner(),
                link.getExpiresAt(), FileCategory.of(f.getContentType(), f.getOriginalName()).id());
    }

    @Operation(summary = "[Công khai] Tải file qua link chia sẻ")
    @GetMapping("/api/public/shares/{token}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String token,
                                                        @RequestParam(defaultValue = "false") boolean inline)
            throws IOException {
        var link = shares.resolve(token);
        FileMeta f = shares.fileOf(link);
        if (!inline) {
            shares.countDownload(link);
        }
        return FileController.stream(files, f, inline);
    }
}
