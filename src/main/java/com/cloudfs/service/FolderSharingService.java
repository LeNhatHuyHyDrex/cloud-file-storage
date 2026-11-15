package com.cloudfs.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.dto.Dtos.FileDto;
import com.cloudfs.dto.Dtos.FolderDto;
import com.cloudfs.model.FileMeta;
import com.cloudfs.model.Folder;
import com.cloudfs.repository.FileMetaRepository;
import com.cloudfs.repository.FolderRepository;
import com.cloudfs.repository.UserRepository;

@Service
public class FolderSharingService {
    public record Invitation(UUID id, UUID folderId, String folderName, String owner, String recipient,
                             String status, Instant createdAt) { }
    public record Link(UUID id, UUID folderId, String token, Instant createdAt, Instant expiresAt) { }
    public record Contents(String rootName, String owner, UUID rootId, UUID folderId, String folderName,
                           List<FolderDto> folders, List<FileDto> files) { }
    private record Grant(UUID folderId, String owner) { }

    private final JdbcTemplate db;
    private final FolderService folderService;
    private final FolderRepository folders;
    private final FileMetaRepository files;
    private final UserRepository users;

    public FolderSharingService(JdbcTemplate db, FolderService folderService, FolderRepository folders,
                                FileMetaRepository files, UserRepository users) {
        this.db = db;
        this.folderService = folderService;
        this.folders = folders;
        this.files = files;
        this.users = users;
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Shared folder not found");
    }

    private List<Invitation> invitationQuery(String sql, Object... args) {
        return db.query(sql, (rs, n) -> new Invitation(rs.getObject("id", UUID.class),
                rs.getObject("folder_id", UUID.class), rs.getString("name"), rs.getString("owner"),
                rs.getString("recipient"), rs.getString("status"), rs.getTimestamp("created_at").toInstant()), args);
    }

    private static final String INVITATIONS = "SELECT i.id, i.folder_id, f.name, i.owner, i.recipient, i.status, i.created_at "
            + "FROM folder_invitations i JOIN folders f ON f.id = i.folder_id ";

    @Transactional
    public Invitation invite(String owner, UUID folderId, String recipient) {
        folderService.findOwned(folderId, owner);
        if (owner.equals(recipient)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You already own this folder");
        if (!users.existsByUsername(recipient)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        UUID id = UUID.randomUUID();
        try {
            db.update("INSERT INTO folder_invitations (id, folder_id, owner, recipient, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    id, folderId, owner, recipient, "PENDING", java.sql.Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User has already been invited");
        }
        return invitationQuery(INVITATIONS + "WHERE i.id = ?", id).get(0);
    }

    public List<Invitation> incoming(String recipient) {
        return invitationQuery(INVITATIONS + "WHERE i.recipient = ? ORDER BY i.created_at DESC", recipient);
    }

    public List<Invitation> sent(String owner, UUID folderId) {
        folderService.findOwned(folderId, owner);
        return invitationQuery(INVITATIONS + "WHERE i.owner = ? AND i.folder_id = ? ORDER BY i.created_at DESC", owner, folderId);
    }

    @Transactional
    public void accept(String recipient, UUID id) {
        int updated = db.update("UPDATE folder_invitations SET status = 'ACCEPTED' WHERE id = ? AND recipient = ? AND status = 'PENDING'", id, recipient);
        if (updated != 1) throw missing();
    }

    @Transactional
    public void decline(String recipient, UUID id) {
        if (db.update("DELETE FROM folder_invitations WHERE id = ? AND recipient = ? AND status = 'PENDING'", id, recipient) != 1) throw missing();
    }

    @Transactional
    public void revokeInvitation(String owner, UUID id) {
        if (db.update("DELETE FROM folder_invitations WHERE id = ? AND owner = ?", id, owner) != 1) throw missing();
    }

    private Grant invitationGrant(String recipient, UUID id) {
        return invitationQuery(INVITATIONS + "WHERE i.id = ? AND i.recipient = ? AND i.status = 'ACCEPTED'", id, recipient)
                .stream().findFirst().map(i -> new Grant(i.folderId(), i.owner())).orElseThrow(this::missing);
    }

    private List<Link> linkQuery(String sql, Object... args) {
        return db.query(sql, (rs, n) -> new Link(rs.getObject("id", UUID.class), rs.getObject("folder_id", UUID.class),
                rs.getString("token"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()), args);
    }

    @Transactional
    public Link createLink(String owner, UUID folderId, Integer hours) {
        folderService.findOwned(folderId, owner);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expires = hours == null ? null : now.plus(Duration.ofHours(hours));
        db.update("INSERT INTO folder_links (id, folder_id, owner, token, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, folderId, owner, ShareService.newToken(), java.sql.Timestamp.from(now),
                expires == null ? null : java.sql.Timestamp.from(expires));
        return linkQuery("SELECT * FROM folder_links WHERE id = ?", id).get(0);
    }

    public List<Link> links(String owner, UUID folderId) {
        folderService.findOwned(folderId, owner);
        return linkQuery("SELECT * FROM folder_links WHERE owner = ? AND folder_id = ? ORDER BY created_at DESC", owner, folderId);
    }

    @Transactional
    public void revokeLink(String owner, UUID id) {
        if (db.update("DELETE FROM folder_links WHERE id = ? AND owner = ?", id, owner) != 1) throw missing();
    }

    private Grant linkGrant(String token) {
        var rows = db.query("SELECT folder_id, owner, expires_at FROM folder_links WHERE token = ?", (rs, n) -> {
            var expires = rs.getTimestamp("expires_at");
            if (expires != null && !expires.toInstant().isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.GONE, "Link has expired");
            return new Grant(rs.getObject("folder_id", UUID.class), rs.getString("owner"));
        }, token);
        return rows.stream().findFirst().orElseThrow(this::missing);
    }

    private Folder inSubtree(Grant grant, UUID folderId) {
        Set<UUID> seen = new HashSet<>();
        UUID current = folderId;
        Folder requested = null;
        while (current != null && seen.add(current)) {
            Folder f = folders.findById(current).filter(x -> x.getOwner().equals(grant.owner())).orElseThrow(this::missing);
            if (requested == null) requested = f;
            if (current.equals(grant.folderId())) return requested;
            current = f.getParentId();
        }
        throw missing();
    }

    private Contents contents(Grant grant, UUID folderId) {
        Folder root = inSubtree(grant, grant.folderId());
        Folder current = inSubtree(grant, folderId == null ? grant.folderId() : folderId);
        return new Contents(root.getName(), grant.owner(), root.getId(), current.getId(), current.getName(),
                folders.findByOwnerAndParentIdOrderByNameAsc(grant.owner(), current.getId()).stream().map(FolderDto::of).toList(),
                files.findByOwnerAndFolderIdOrderByOriginalNameAsc(grant.owner(), current.getId()).stream().map(FileDto::of).toList());
    }

    private FileMeta file(Grant grant, UUID fileId) {
        FileMeta f = files.findById(fileId).filter(x -> x.getOwner().equals(grant.owner())).orElseThrow(this::missing);
        if (f.getFolderId() == null) throw missing();
        inSubtree(grant, f.getFolderId());
        return f;
    }

    public Contents invitationContents(String user, UUID id, UUID folderId) { return contents(invitationGrant(user, id), folderId); }
    public FileMeta invitationFile(String user, UUID id, UUID fileId) { return file(invitationGrant(user, id), fileId); }
    public Contents linkContents(String token, UUID folderId) { return contents(linkGrant(token), folderId); }
    public FileMeta linkFile(String token, UUID fileId) { return file(linkGrant(token), fileId); }
}
