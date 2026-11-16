package com.cloudfs.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.cloudfs.model.FileMeta;

public interface FileMetaRepository extends JpaRepository<FileMeta, UUID> {

    List<FileMeta> findByOwnerOrderByUploadedAtDesc(String owner);

    List<FileMeta> findByOwnerAndFolderIdIsNullOrderByOriginalNameAsc(String owner);

    List<FileMeta> findByOwnerAndFolderIdOrderByOriginalNameAsc(String owner, UUID folderId);

    List<FileMeta> findByOwnerAndFolderIdIn(String owner, Collection<UUID> folderIds);

    List<FileMeta> findByOwnerAndStarredTrueOrderByOriginalNameAsc(String owner);

    List<FileMeta> findTop20ByOwnerOrderByUploadedAtDesc(String owner);

    List<FileMeta> findByOwnerAndOriginalNameContainingIgnoreCaseOrderByOriginalNameAsc(String owner, String q);

    long countByOwner(String owner);

    @Query(value = "select coalesce(sum(size), 0) from files where owner = :owner", nativeQuery = true)
    long totalSizeByOwner(String owner);
}
