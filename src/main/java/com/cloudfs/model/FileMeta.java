package com.cloudfs.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Metadata cua file; noi dung file nam trong object storage duoi khoa objectKey. */
@Entity
@Table(name = "files")
public class FileMeta {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String objectKey;

    @Column(nullable = false)
    private String originalName;

    private String contentType;

    private long size;

    @Column(nullable = false)
    private String owner;

    /** null = nam o thu muc goc. */
    private UUID folderId;

    private boolean starred;

    @Column(nullable = false)
    private Instant uploadedAt;

    protected FileMeta() {
    }

    public FileMeta(UUID id, String objectKey, String originalName, String contentType, long size,
                    String owner, UUID folderId) {
        this.id = id;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.owner = owner;
        this.folderId = folderId;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getOwner() {
        return owner;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
    }

    public boolean isStarred() {
        return starred;
    }

    public void setStarred(boolean starred) {
        this.starred = starred;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
