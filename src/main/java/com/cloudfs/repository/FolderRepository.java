package com.cloudfs.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cloudfs.model.Folder;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByOwnerAndParentIdIsNullOrderByNameAsc(String owner);

    List<Folder> findByOwnerAndParentIdOrderByNameAsc(String owner, UUID parentId);

    List<Folder> findByOwner(String owner);

    long countByOwner(String owner);
}
