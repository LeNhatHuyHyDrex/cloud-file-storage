package com.cloudfs.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.cloudfs.model.FileMeta;
import com.cloudfs.model.Folder;
import com.cloudfs.model.ShareLink;
import com.cloudfs.service.FileCategory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cac doi tuong vao/ra cua REST API. */
public final class Dtos {

    private Dtos() {
    }

    public record Credentials(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "chi gom chu, so, dau . _ -") String username,
            @NotBlank @Size(min = 6, max = 100) String password) {
    }

    public record AuthResponse(String token, String username, long expiresInMs) {
    }

    public record FileDto(UUID id, String name, String contentType, long size, UUID folderId,
                          boolean starred, Instant uploadedAt, String category) {
        public static FileDto of(FileMeta f) {
            return new FileDto(f.getId(), f.getOriginalName(), f.getContentType(), f.getSize(), f.getFolderId(),
                    f.isStarred(), f.getUploadedAt(), FileCategory.of(f.getContentType(), f.getOriginalName()).id());
        }
    }

    public record FolderDto(UUID id, String name, UUID parentId, Instant createdAt) {
        public static FolderDto of(Folder f) {
            return new FolderDto(f.getId(), f.getName(), f.getParentId(), f.getCreatedAt());
        }
    }

    public record ShareDto(UUID id, String token, UUID fileId, String fileName, Instant createdAt,
                           Instant expiresAt, int downloads, boolean expired) {
        public static ShareDto of(ShareLink s, String fileName) {
            return new ShareDto(s.getId(), s.getToken(), s.getFileId(), fileName, s.getCreatedAt(),
                    s.getExpiresAt(), s.getDownloadCount(), s.isExpired());
        }
    }

    public record PublicShareDto(String fileName, String contentType, long size, String owner,
                                 Instant expiresAt, String category) {
    }

    public record ProfileDto(String username, Instant memberSince, long usedBytes, long quotaBytes,
                             long fileCount, long folderCount, String storageBackend,
                             Map<String, Long> bytesByCategory) {
    }

    public record NameRequest(@NotBlank @Size(max = 255) String name) {
    }

    public record CreateFolderRequest(@NotBlank @Size(max = 255) String name, UUID parentId) {
    }

    /** folderId = null nghia la chuyen ve thu muc goc. */
    public record MoveRequest(UUID folderId) {
    }

    public record StarRequest(boolean starred) {
    }

    /** expiresInHours = null -> link khong het han. */
    public record CreateShareRequest(@Min(1) @Max(24 * 30) Integer expiresInHours) {
    }

    public record PresignDto(String url, Instant expiresAt) {
    }
}
