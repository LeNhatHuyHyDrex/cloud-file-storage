package com.cloudfs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.cloudfs.model.AppUser;
import com.cloudfs.repository.FileMetaRepository;
import com.cloudfs.repository.UserRepository;
import com.cloudfs.service.FolderService;
import com.cloudfs.service.FolderSharingService;

/** Kiem tra Flyway migration + JPA mapping chay dung tren PostgreSQL that (giong production). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    UserRepository users;

    @Autowired
    FileMetaRepository files;

    @Autowired
    FolderService folders;

    @Autowired
    FolderSharingService sharing;

    @Test
    void schemaMatchesEntities() {
        users.save(new AppUser("pg-user", "hash", 1024));
        assertEquals(1024, users.findByUsername("pg-user").orElseThrow().getQuotaBytes());
        assertEquals(0, files.totalSizeByOwner("pg-user"));
    }

    @Test
    void folderSharingWorksWithPostgres() {
        users.save(new AppUser("pg-share-owner", "hash", 1024));
        users.save(new AppUser("pg-share-recipient", "hash", 1024));
        var folder = folders.create("pg-share-owner", "Shared", null);
        var invitation = sharing.invite("pg-share-owner", folder.getId(), "pg-share-recipient");
        assertEquals("PENDING", sharing.incoming("pg-share-recipient").get(0).status());
        sharing.accept("pg-share-recipient", invitation.id());
        assertEquals("Shared", sharing.invitationContents("pg-share-recipient", invitation.id(), null).rootName());
        var link = sharing.createLink("pg-share-owner", folder.getId(), 24);
        assertEquals("Shared", sharing.linkContents(link.token(), null).folderName());
    }
}
