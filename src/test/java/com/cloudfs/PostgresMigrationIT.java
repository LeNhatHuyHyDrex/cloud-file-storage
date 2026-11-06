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

    @Test
    void schemaMatchesEntities() {
        users.save(new AppUser("pg-user", "hash", 1024));
        assertEquals(1024, users.findByUsername("pg-user").orElseThrow().getQuotaBytes());
        assertEquals(0, files.totalSizeByOwner("pg-user"));
    }
}
