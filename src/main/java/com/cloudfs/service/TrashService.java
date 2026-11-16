package com.cloudfs.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.storage.ObjectStorage;

@Service
public class TrashService {
    public record TrashItem(UUID id, String name, String type, Instant deletedAt, Instant expiresAt) { }
    private record FolderRow(UUID id, UUID parentId, Instant deletedAt) { }
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final Logger log = LoggerFactory.getLogger(TrashService.class);
    private final JdbcTemplate db;
    private final FileService files;
    private final FolderService folders;
    private final ObjectStorage storage;

    public TrashService(JdbcTemplate db, FileService files, FolderService folders, ObjectStorage storage) {
        this.db = db; this.files = files; this.folders = folders; this.storage = storage;
    }

    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in trash"); }

    public List<TrashItem> list(String owner) {
        String sql = "SELECT id, original_name AS name, deleted_at, 'file' AS kind FROM files WHERE owner = ? AND deleted_at IS NOT NULL AND id = trash_batch "
                + "UNION ALL SELECT id, name, deleted_at, 'folder' AS kind FROM folders WHERE owner = ? AND deleted_at IS NOT NULL AND id = trash_batch ORDER BY deleted_at DESC";
        return db.query(sql, (rs, n) -> {
            Instant deleted = rs.getTimestamp("deleted_at").toInstant();
            return new TrashItem(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("kind"), deleted, deleted.plus(RETENTION));
        }, owner, owner);
    }

    @Transactional
    public void trashFile(String owner, UUID id) {
        files.findOwned(id, owner);
        if (db.update("UPDATE files SET deleted_at = ?, trash_batch = ? WHERE id = ? AND owner = ? AND deleted_at IS NULL", Timestamp.from(Instant.now()), id, id, owner) != 1) throw missing();
    }

    private List<FolderRow> allFolders(String owner) {
        return db.query("SELECT id, parent_id, deleted_at FROM folders WHERE owner = ? FOR UPDATE", (rs, n) -> {
            Timestamp deleted = rs.getTimestamp("deleted_at");
            return new FolderRow(rs.getObject("id", UUID.class), rs.getObject("parent_id", UUID.class), deleted == null ? null : deleted.toInstant());
        }, owner);
    }

    private List<FolderRow> subtree(String owner, UUID root) {
        Map<UUID, List<FolderRow>> children = new HashMap<>();
        Map<UUID, FolderRow> byId = new HashMap<>();
        for (FolderRow f : allFolders(owner)) {
            byId.put(f.id(), f);
            if (f.parentId() != null) children.computeIfAbsent(f.parentId(), k -> new ArrayList<>()).add(f);
        }
        FolderRow start = byId.get(root);
        if (start == null) throw missing();
        List<FolderRow> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        var queue = new ArrayDeque<FolderRow>(); queue.add(start);
        while (!queue.isEmpty()) {
            FolderRow f = queue.remove();
            if (visited.add(f.id())) { result.add(f); queue.addAll(children.getOrDefault(f.id(), List.of())); }
        }
        return result;
    }

    @Transactional
    public void trashFolder(String owner, UUID id) {
        folders.findOwned(id, owner);
        Timestamp now = Timestamp.from(Instant.now());
        for (FolderRow folder : subtree(owner, id)) {
            if (folder.deletedAt() != null) continue;
            db.update("UPDATE files SET deleted_at = ?, trash_batch = ? WHERE owner = ? AND folder_id = ? AND deleted_at IS NULL", now, id, owner, folder.id());
            db.update("UPDATE folders SET deleted_at = ?, trash_batch = ? WHERE owner = ? AND id = ? AND deleted_at IS NULL", now, id, owner, folder.id());
        }
    }

    private void requireRoot(String table, String owner, UUID id) {
        var rows = db.query("SELECT deleted_at FROM " + table + " WHERE id = ? AND owner = ? AND deleted_at IS NOT NULL AND trash_batch = id FOR UPDATE", (rs, n) -> rs.getTimestamp(1).toInstant(), id, owner);
        if (rows.size() != 1) throw missing();
    }

    private void requireRestorable(String table, String owner, UUID id) {
        requireRoot(table, owner, id);
        Timestamp cutoff = Timestamp.from(Instant.now().minus(RETENTION));
        Integer count = db.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ? AND owner = ? AND deleted_at > ?", Integer.class, id, owner, cutoff);
        if (count == null || count != 1) throw new ResponseStatusException(HttpStatus.GONE, "The 30-day recovery period has expired");
    }

    @Transactional
    public void restoreFile(String owner, UUID id) {
        requireRestorable("files", owner, id);
        db.update("UPDATE files SET folder_id = NULL WHERE id = ? AND owner = ? AND folder_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM folders f WHERE f.id = files.folder_id AND f.owner = ? AND f.deleted_at IS NULL)", id, owner, owner);
        db.update("UPDATE files SET deleted_at = NULL, trash_batch = NULL WHERE id = ? AND owner = ?", id, owner);
    }

    @Transactional
    public void restoreFolder(String owner, UUID id) {
        requireRestorable("folders", owner, id);
        db.update("UPDATE folders SET parent_id = NULL WHERE id = ? AND owner = ? AND parent_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM folders p WHERE p.id = folders.parent_id AND p.owner = ? AND p.deleted_at IS NULL)", id, owner, owner);
        db.update("UPDATE folders SET deleted_at = NULL, trash_batch = NULL WHERE owner = ? AND trash_batch = ?", owner, id);
        db.update("UPDATE files SET deleted_at = NULL, trash_batch = NULL WHERE owner = ? AND trash_batch = ?", owner, id);
    }

    private void queueFile(String owner, UUID id) {
        var keys = db.query("SELECT object_key FROM files WHERE id = ? AND owner = ? AND deleted_at IS NOT NULL FOR UPDATE", (rs, n) -> rs.getString(1), id, owner);
        if (keys.size() != 1) throw missing();
        db.update("INSERT INTO pending_object_deletions (object_key, created_at) VALUES (?, ?)", keys.get(0), Timestamp.from(Instant.now()));
        db.update("DELETE FROM files WHERE id = ? AND owner = ? AND deleted_at IS NOT NULL", id, owner);
    }

    @Transactional
    public void purgeFile(String owner, UUID id) {
        requireRoot("files", owner, id);
        queueFile(owner, id);
    }

    @Transactional
    public void purgeFolder(String owner, UUID id) {
        requireRoot("folders", owner, id);
        List<FolderRow> tree = subtree(owner, id);
        if (tree.stream().anyMatch(f -> f.deletedAt() == null)) throw new IllegalStateException("Cannot purge an active subfolder");
        for (FolderRow f : tree) {
            List<UUID> ids = db.query("SELECT id FROM files WHERE owner = ? AND folder_id = ? AND deleted_at IS NOT NULL", (rs, n) -> rs.getObject(1, UUID.class), owner, f.id());
            Integer active = db.queryForObject("SELECT count(*) FROM files WHERE owner = ? AND folder_id = ? AND deleted_at IS NULL", Integer.class, owner, f.id());
            if (active != null && active > 0) throw new IllegalStateException("Cannot purge a folder containing active files");
            for (UUID fileId : ids) queueFile(owner, fileId);
        }
        db.update("DELETE FROM folders WHERE id = ? AND owner = ? AND deleted_at IS NOT NULL", id, owner);
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 30000)
    @Transactional
    public void purgeExpired() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(RETENTION));
        record Item(UUID id, String owner) { }
        var expiredFolders = db.query("SELECT id, owner FROM folders WHERE deleted_at <= ? AND id = trash_batch", (rs, n) -> new Item(rs.getObject(1, UUID.class), rs.getString(2)), cutoff);
        var expiredFiles = db.query("SELECT id, owner FROM files WHERE deleted_at <= ? AND id = trash_batch", (rs, n) -> new Item(rs.getObject(1, UUID.class), rs.getString(2)), cutoff);
        for (Item item : expiredFiles) purgeFile(item.owner(), item.id());
        for (Item item : expiredFolders) {
            Integer remaining = db.queryForObject("SELECT count(*) FROM folders WHERE id = ? AND owner = ? AND id = trash_batch", Integer.class, item.id(), item.owner());
            if (remaining != null && remaining > 0) purgeFolder(item.owner(), item.id());
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public synchronized void deleteQueuedObjects() {
        List<String> keys = db.query("SELECT object_key FROM pending_object_deletions ORDER BY created_at LIMIT 100", (rs, n) -> rs.getString(1));
        for (String key : keys) {
            try { storage.deletePermanently(key); db.update("DELETE FROM pending_object_deletions WHERE object_key = ?", key); }
            catch (RuntimeException e) { log.warn("Unable to remove a deleted object; it will be retried", e); }
        }
    }
}
