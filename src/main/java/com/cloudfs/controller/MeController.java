package com.cloudfs.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.dto.Dtos.ProfileDto;
import com.cloudfs.model.AppUser;
import com.cloudfs.model.FileMeta;
import com.cloudfs.repository.FolderRepository;
import com.cloudfs.repository.UserRepository;
import com.cloudfs.service.FileCategory;
import com.cloudfs.service.FileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Account")
@SecurityRequirement(name = "bearer")
public class MeController {

    private final UserRepository users;
    private final FolderRepository folders;
    private final FileService fileService;

    public MeController(UserRepository users, FolderRepository folders, FileService fileService) {
        this.users = users;
        this.folders = folders;
        this.fileService = fileService;
    }

    @Operation(summary = "Thông tin tài khoản, dung lượng đã dùng và thống kê theo loại file")
    @GetMapping
    public ProfileDto me(Authentication auth) {
        AppUser user = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (FileCategory c : FileCategory.values()) {
            byCategory.put(c.id(), 0L);
        }
        long used = 0;
        long count = 0;
        for (FileMeta f : fileService.all(user.getUsername())) {
            byCategory.merge(FileCategory.of(f.getContentType(), f.getOriginalName()).id(), f.getSize(), Long::sum);
            used += f.getSize();
            count++;
        }
        return new ProfileDto(user.getUsername(), user.getCreatedAt(), used, user.getQuotaBytes(), count,
                folders.countByOwner(user.getUsername()), fileService.backendName(), byCategory);
    }
}
