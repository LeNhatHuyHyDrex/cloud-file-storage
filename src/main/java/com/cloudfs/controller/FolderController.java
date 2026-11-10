package com.cloudfs.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cloudfs.dto.Dtos.CreateFolderRequest;
import com.cloudfs.dto.Dtos.FolderDto;
import com.cloudfs.dto.Dtos.NameRequest;
import com.cloudfs.service.FolderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/folders")
@Tag(name = "Folders")
@SecurityRequirement(name = "bearer")
public class FolderController {

    private final FolderService folders;

    public FolderController(FolderService folders) {
        this.folders = folders;
    }

    @Operation(summary = "Subfolders of `parentId` (empty = root); `all=true` returns every folder")
    @GetMapping
    public List<FolderDto> list(@RequestParam(required = false) UUID parentId,
                                @RequestParam(defaultValue = "false") boolean all,
                                Authentication auth) {
        var result = all ? folders.allOf(auth.getName()) : folders.list(auth.getName(), parentId);
        return result.stream().map(FolderDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderDto create(@Valid @RequestBody CreateFolderRequest body, Authentication auth) {
        return FolderDto.of(folders.create(auth.getName(), body.name(), body.parentId()));
    }

    @Operation(summary = "Path from root to the folder (breadcrumb)")
    @GetMapping("/{id}/path")
    public List<FolderDto> path(@PathVariable UUID id, Authentication auth) {
        return folders.path(auth.getName(), id).stream().map(FolderDto::of).toList();
    }

    @PatchMapping("/{id}")
    public FolderDto rename(@PathVariable UUID id, @Valid @RequestBody NameRequest body, Authentication auth) {
        return FolderDto.of(folders.rename(auth.getName(), id, body.name()));
    }

    @Operation(summary = "Delete a folder and everything inside it")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        folders.delete(auth.getName(), id);
    }
}
