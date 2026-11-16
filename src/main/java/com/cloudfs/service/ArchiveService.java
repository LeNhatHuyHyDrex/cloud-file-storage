package com.cloudfs.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.model.FileMeta;
import com.cloudfs.model.Folder;
import com.cloudfs.repository.FileMetaRepository;
import com.cloudfs.repository.FolderRepository;

@Service
public class ArchiveService {
    public record Entry(String path, FileMeta file) { }
    private record Pending(Folder folder, String parent) { }
    private final FileService files;
    private final FolderService folders;
    private final FileMetaRepository fileRepository;
    private final FolderRepository folderRepository;

    public ArchiveService(FileService files, FolderService folders, FileMetaRepository fileRepository, FolderRepository folderRepository) {
        this.files = files; this.folders = folders; this.fileRepository = fileRepository; this.folderRepository = folderRepository;
    }

    public List<Entry> plan(String owner, List<UUID> fileIds, List<UUID> folderIds) {
        if (fileIds.size() + folderIds.size() == 0 || fileIds.size() + folderIds.size() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select between 1 and 1000 items");
        List<FileMeta> requestedFiles = fileIds.stream().map(id -> files.findOwned(id, owner)).toList();
        List<Folder> requestedFolders = folderIds.stream().map(id -> folders.findOwned(id, owner)).toList();
        return planAuthorized(owner, requestedFiles, requestedFolders);
    }

    public List<Entry> planFolder(Folder root) { return planAuthorized(root.getOwner(), List.of(), List.of(root)); }

    private List<Entry> planAuthorized(String owner, List<FileMeta> selectedFiles, List<Folder> selectedFolders) {
        Map<UUID, List<Folder>> children = new HashMap<>();
        Map<UUID, Folder> allFolders = new HashMap<>();
        for (Folder f : folderRepository.findByOwner(owner)) {
            allFolders.put(f.getId(), f);
            if (f.getParentId() != null) children.computeIfAbsent(f.getParentId(), k -> new ArrayList<>()).add(f);
        }
        Map<UUID, List<FileMeta>> fileChildren = new HashMap<>();
        for (FileMeta f : fileRepository.findByOwnerOrderByUploadedAtDesc(owner)) {
            if (f.getFolderId() != null) fileChildren.computeIfAbsent(f.getFolderId(), k -> new ArrayList<>()).add(f);
        }
        children.values().forEach(list -> list.sort(Comparator.comparing(Folder::getName)));
        fileChildren.values().forEach(list -> list.sort(Comparator.comparing(FileMeta::getOriginalName)));
        var entries = new ArrayList<Entry>();
        var paths = new HashSet<String>();
        var seenFiles = new HashSet<UUID>();
        var seenFolders = new HashSet<UUID>();
        var queue = new ArrayDeque<Pending>();
        Set<UUID> selectedIds = new HashSet<>();
        selectedFolders.forEach(f -> selectedIds.add(f.getId()));
        for (Folder folder : selectedFolders) {
            UUID parent = folder.getParentId();
            Set<UUID> ancestors = new HashSet<>();
            boolean included = false;
            while (parent != null && ancestors.add(parent)) {
                if (selectedIds.contains(parent)) { included = true; break; }
                Folder ancestor = allFolders.get(parent);
                parent = ancestor == null ? null : ancestor.getParentId();
            }
            if (!included) queue.add(new Pending(folder, ""));
        }
        while (!queue.isEmpty()) {
            Pending p = queue.remove();
            if (!seenFolders.add(p.folder().getId())) continue;
            String dir = unique(p.parent(), p.folder().getName(), paths) + "/";
            entries.add(new Entry(dir, null));
            for (FileMeta f : fileChildren.getOrDefault(p.folder().getId(), List.of())) {
                if (seenFiles.add(f.getId())) entries.add(new Entry(unique(dir, f.getOriginalName(), paths), f));
            }
            for (Folder child : children.getOrDefault(p.folder().getId(), List.of())) queue.add(new Pending(child, dir));
            if (entries.size() + queue.size() > 10000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Archive has too many items");
        }
        for (FileMeta f : selectedFiles) {
            if (seenFiles.add(f.getId())) entries.add(new Entry(unique("", f.getOriginalName(), paths), f));
        }
        return entries;
    }

    private String unique(String parent, String raw, Set<String> used) {
        String name = FileNames.clean(raw).replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[ .]+$", "");
        if (name.isBlank()) name = "item";
        String candidate = name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; !used.add((parent + candidate).toLowerCase(Locale.ROOT)); n++) candidate = stem + " (" + n + ")" + ext;
        return parent + candidate;
    }

    public void write(List<Entry> entries, OutputStream output) throws IOException {
        try (var zip = new ZipOutputStream(output, java.nio.charset.StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.path()));
                if (entry.file() != null) try (var input = files.open(entry.file())) { input.transferTo(zip); }
                zip.closeEntry();
            }
        }
    }
}
