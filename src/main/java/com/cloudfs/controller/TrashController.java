package com.cloudfs.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cloudfs.service.TrashService;

@RestController
@RequestMapping("/api/trash")
public class TrashController {
    private final TrashService trash;
    public TrashController(TrashService trash) { this.trash = trash; }

    @GetMapping
    public List<TrashService.TrashItem> list(Authentication auth) { return trash.list(auth.getName()); }
    @PostMapping("/files/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreFile(@PathVariable UUID id, Authentication auth) { trash.restoreFile(auth.getName(), id); }
    @PostMapping("/folders/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreFolder(@PathVariable UUID id, Authentication auth) { trash.restoreFolder(auth.getName(), id); }
    @DeleteMapping("/files/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeFile(@PathVariable UUID id, Authentication auth) { trash.purgeFile(auth.getName(), id); }
    @DeleteMapping("/folders/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeFolder(@PathVariable UUID id, Authentication auth) { trash.purgeFolder(auth.getName(), id); }
}
