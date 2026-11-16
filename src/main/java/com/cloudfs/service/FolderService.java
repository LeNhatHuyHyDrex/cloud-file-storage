package com.cloudfs.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.model.Folder;
import com.cloudfs.repository.FolderRepository;

@Service
public class FolderService {

    private final FolderRepository folders;
    public FolderService(FolderRepository folders) {
        this.folders = folders;
    }

    /** Chi chu so huu moi thay duoc thu muc; nguoi khac nhan 404. */
    public Folder findOwned(UUID id, String owner) {
        return folders.findById(id)
                .filter(f -> f.getOwner().equals(owner))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found"));
    }

    public List<Folder> list(String owner, UUID parentId) {
        if (parentId == null) {
            return folders.findByOwnerAndParentIdIsNullOrderByNameAsc(owner);
        }
        findOwned(parentId, owner);
        return folders.findByOwnerAndParentIdOrderByNameAsc(owner, parentId);
    }

    public List<Folder> allOf(String owner) {
        return folders.findByOwner(owner);
    }

    @Transactional
    public Folder create(String owner, String name, UUID parentId) {
        if (parentId != null) {
            findOwned(parentId, owner);
        }
        return folders.save(new Folder(FileNames.clean(name), parentId, owner));
    }

    @Transactional
    public Folder rename(String owner, UUID id, String name) {
        Folder folder = findOwned(id, owner);
        folder.setName(FileNames.clean(name));
        return folder;
    }

    /** Duong dan tu goc toi thu muc (dung cho breadcrumb). */
    public List<Folder> path(String owner, UUID id) {
        List<Folder> path = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        UUID current = id;
        while (current != null && seen.add(current)) {
            Folder f = findOwned(current, owner);
            path.add(f);
            current = f.getParentId();
        }
        Collections.reverse(path);
        return path;
    }

}
