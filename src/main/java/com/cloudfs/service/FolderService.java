package com.cloudfs.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.model.FileMeta;
import com.cloudfs.model.Folder;
import com.cloudfs.repository.FileMetaRepository;
import com.cloudfs.repository.FolderRepository;

@Service
public class FolderService {

    private final FolderRepository folders;
    private final FileMetaRepository files;
    private final FileService fileService;

    public FolderService(FolderRepository folders, FileMetaRepository files, FileService fileService) {
        this.folders = folders;
        this.files = files;
        this.fileService = fileService;
    }

    /** Chi chu so huu moi thay duoc thu muc; nguoi khac nhan 404. */
    public Folder findOwned(UUID id, String owner) {
        return folders.findById(id)
                .filter(f -> f.getOwner().equals(owner))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thư mục"));
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

    /** Xoa thu muc cung toan bo thu muc con va file ben trong (ca object tren storage). */
    @Transactional
    public void delete(String owner, UUID id) {
        Folder root = findOwned(id, owner);
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (Folder f : folders.findByOwner(owner)) {
            if (f.getParentId() != null) {
                children.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f.getId());
            }
        }
        Set<UUID> subtree = new HashSet<>();
        var queue = new ArrayDeque<UUID>(List.of(root.getId()));
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            if (subtree.add(cur)) {
                queue.addAll(children.getOrDefault(cur, List.of()));
            }
        }
        List<FileMeta> inside = files.findByOwnerAndFolderIdIn(owner, subtree);
        fileService.deleteAll(inside);
        // FK parent_id ON DELETE CASCADE xoa not thu muc con
        folders.delete(root);
    }
}
