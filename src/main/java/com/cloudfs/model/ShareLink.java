package com.cloudfs.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Link cong khai de nguoi khong dang nhap tai 1 file. */
@Entity
@Table(name = "share_links")
public class ShareLink {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private UUID fileId;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private Instant createdAt;

    /** null = khong het han. */
    private Instant expiresAt;

    private int downloadCount;

    protected ShareLink() {
    }

    public ShareLink(String token, UUID fileId, String owner, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.token = token;
        this.fileId = fileId;
        this.owner = owner;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public void incrementDownloads() {
        downloadCount++;
    }

    public UUID getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getOwner() {
        return owner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getDownloadCount() {
        return downloadCount;
    }
}
