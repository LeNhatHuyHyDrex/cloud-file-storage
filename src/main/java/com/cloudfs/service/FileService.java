package com.cloudfs.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudfs.model.AppUser;
import com.cloudfs.model.FileMeta;
import com.cloudfs.repository.FileMetaRepository;
import com.cloudfs.repository.FolderRepository;
import com.cloudfs.repository.UserRepository;
import com.cloudfs.storage.ObjectStorage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/** Nghiep vu file: ghi metadata vao DB, noi dung vao object storage, kiem tra quyen va quota. */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** Kieu duoc phep hien thi inline; cac kieu khac (html, svg...) bi ep tai ve de tranh XSS. */
    private static final Set<String> INLINE_SAFE_PREFIX = Set.of("image/png", "image/jpeg", "image/gif", "image/webp",
            "image/bmp", "application/pdf", "text/plain", "video/", "audio/");

    private final FileMetaRepository files;
    private final FolderRepository folders;
    private final UserRepository users;
    private final ObjectStorage storage;
    private final Duration presignTtl;
    private final Counter uploads;
    private final DistributionSummary uploadBytes;

    public FileService(FileMetaRepository files, FolderRepository folders, UserRepository users,
                       ObjectStorage storage, MeterRegistry metrics,
                       @Value("${app.s3.presign-ttl-minutes:15}") long presignTtlMinutes) {
        this.files = files;
        this.folders = folders;
        this.users = users;
        this.storage = storage;
        this.presignTtl = Duration.ofMinutes(presignTtlMinutes);
        this.uploads = Counter.builder("nimbus.files.uploaded").description("So file da upload").register(metrics);
        this.uploadBytes = DistributionSummary.builder("nimbus.files.upload.bytes").baseUnit("bytes")
                .description("Kich thuoc file upload").register(metrics);
    }

    /** Chi chu so huu moi truy cap duoc; nguoi khac nhan 404 de khong lo su ton tai cua file. */
    public FileMeta findOwned(UUID id, String owner) {
        return files.findById(id)
                .filter(f -> f.getOwner().equals(owner))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    private void requireFolder(UUID folderId, String owner) {
        if (folderId != null && folders.findById(folderId).filter(f -> f.getOwner().equals(owner)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Folder not found");
        }
    }

    public FileMeta upload(MultipartFile file, String owner, UUID folderId) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        requireFolder(folderId, owner);
        AppUser user = users.findByUsername(owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        long used = files.totalSizeByOwner(owner);
        if (used + file.getSize() > user.getQuotaBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded");
        }

        String name = FileNames.clean(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");
        String type = file.getContentType();
        if (type == null || type.isBlank() || type.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
            type = MediaTypeFactory.getMediaType(name).map(MediaType::toString)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        UUID id = UUID.randomUUID();
        String key = owner + "/" + id;
        try (InputStream in = file.getInputStream()) {
            storage.put(key, in, file.getSize(), type);
        }
        try {
            FileMeta meta = files.save(new FileMeta(id, key, name, type, file.getSize(), owner, folderId));
            uploads.increment();
            uploadBytes.record(file.getSize());
            return meta;
        } catch (RuntimeException e) {
            // Khong de lai object mo coi neu ghi DB that bai
            storage.delete(key);
            throw e;
        }
    }

    public List<FileMeta> list(String owner, UUID folderId) {
        if (folderId == null) {
            return files.findByOwnerAndFolderIdIsNullOrderByOriginalNameAsc(owner);
        }
        requireFolder(folderId, owner);
        return files.findByOwnerAndFolderIdOrderByOriginalNameAsc(owner, folderId);
    }

    public List<FileMeta> search(String owner, String q) {
        return files.findByOwnerAndOriginalNameContainingIgnoreCaseOrderByOriginalNameAsc(owner, q.strip());
    }

    public List<FileMeta> starred(String owner) {
        return files.findByOwnerAndStarredTrueOrderByOriginalNameAsc(owner);
    }

    public List<FileMeta> recent(String owner) {
        return files.findTop20ByOwnerOrderByUploadedAtDesc(owner);
    }

    public List<FileMeta> all(String owner) {
        return files.findByOwnerOrderByUploadedAtDesc(owner);
    }

    public InputStream open(FileMeta meta) throws IOException {
        return storage.get(meta.getObjectKey());
    }

    public boolean inlineSafe(FileMeta meta) {
        String type = meta.getContentType() == null ? "" : meta.getContentType();
        return INLINE_SAFE_PREFIX.stream().anyMatch(type::startsWith);
    }

    public Optional<URL> presign(FileMeta meta) {
        return storage.presignDownload(meta.getObjectKey(), meta.getOriginalName(), presignTtl);
    }

    public Duration presignTtl() {
        return presignTtl;
    }

    @Transactional
    public FileMeta rename(String owner, UUID id, String name) {
        FileMeta meta = findOwned(id, owner);
        meta.setOriginalName(FileNames.clean(name));
        return files.save(meta);
    }

    @Transactional
    public FileMeta move(String owner, UUID id, UUID folderId) {
        FileMeta meta = findOwned(id, owner);
        requireFolder(folderId, owner);
        meta.setFolderId(folderId);
        return files.save(meta);
    }

    @Transactional
    public FileMeta star(String owner, UUID id, boolean starred) {
        FileMeta meta = findOwned(id, owner);
        meta.setStarred(starred);
        return files.save(meta);
    }

    @Transactional
    public void delete(String owner, UUID id) {
        deleteAll(List.of(findOwned(id, owner)));
    }

    /** Xoa metadata trong transaction; chi xoa object tren storage SAU KHI commit thanh cong. */
    @Transactional
    public void deleteAll(Collection<FileMeta> metas) {
        if (metas.isEmpty()) {
            return;
        }
        List<String> keys = metas.stream().map(FileMeta::getObjectKey).toList();
        files.deleteAll(metas);
        files.flush();
        Runnable removeObjects = () -> keys.forEach(k -> {
            try {
                storage.delete(k);
            } catch (RuntimeException e) {
                log.warn("Could not delete object {}: {}", k, e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    removeObjects.run();
                }
            });
        } else {
            removeObjects.run();
        }
    }

    public String backendName() {
        return storage.backendName();
    }
}
