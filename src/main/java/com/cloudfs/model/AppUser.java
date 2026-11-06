package com.cloudfs.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    /** Dung luong toi da (byte) user duoc phep luu. */
    @Column(nullable = false)
    private long quotaBytes;

    @Column(nullable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, long quotaBytes) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.quotaBytes = quotaBytes;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public long getQuotaBytes() {
        return quotaBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
