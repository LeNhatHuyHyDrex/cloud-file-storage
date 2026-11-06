package com.cloudfs.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Thu muc ao: chi ton tai trong DB, object tren S3 van nam phang theo owner/uuid. */
@Entity
@Table(name = "folders")
public class Folder {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** null = thu muc goc. */
    private UUID parentId;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private Instant createdAt;

    protected Folder() {
    }

    public Folder(String name, UUID parentId, String owner) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.parentId = parentId;
        this.owner = owner;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getOwner() {
        return owner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
