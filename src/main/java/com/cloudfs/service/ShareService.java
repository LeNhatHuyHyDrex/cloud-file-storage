package com.cloudfs.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.model.FileMeta;
import com.cloudfs.model.ShareLink;
import com.cloudfs.repository.FileMetaRepository;
import com.cloudfs.repository.ShareLinkRepository;

@Service
public class ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkRepository shares;
    private final FileMetaRepository files;
    private final FileService fileService;

    public ShareService(ShareLinkRepository shares, FileMetaRepository files, FileService fileService) {
        this.shares = shares;
        this.files = files;
        this.fileService = fileService;
    }

    /** Token 192-bit ngau nhien, URL-safe: khong the doan. */
    static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public ShareLink create(String owner, UUID fileId, Integer expiresInHours) {
        fileService.findOwned(fileId, owner);
        Instant expires = expiresInHours == null ? null : Instant.now().plus(Duration.ofHours(expiresInHours));
        return shares.save(new ShareLink(newToken(), fileId, owner, expires));
    }

    public List<ShareLink> list(String owner) {
        return shares.findByOwnerOrderByCreatedAtDesc(owner);
    }

    @Transactional
    public void revoke(String owner, UUID id) {
        ShareLink link = shares.findById(id)
                .filter(s -> s.getOwner().equals(owner))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy link"));
        shares.delete(link);
    }

    /** Tra ve link con hieu luc; 404 neu khong ton tai, 410 neu da het han. */
    public ShareLink resolve(String token) {
        ShareLink link = shares.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link không tồn tại"));
        if (link.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Link đã hết hạn");
        }
        return link;
    }

    public FileMeta fileOf(ShareLink link) {
        return files.findById(link.getFileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File đã bị xóa"));
    }

    @Transactional
    public void countDownload(ShareLink link) {
        shares.findById(link.getId()).ifPresent(ShareLink::incrementDownloads);
    }

    public String fileName(ShareLink link) {
        return files.findById(link.getFileId()).map(FileMeta::getOriginalName).orElse("(đã xóa)");
    }
}
