package com.cloudfs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class UnitTests {

    @Test
    void fileNamesStripPathsAndControlChars() {
        assertEquals("passwd", FileNames.clean("../../etc/passwd"));
        assertEquals("a.txt", FileNames.clean("C:\\Users\\x\\a.txt"));
        assertEquals("ab.txt", FileNames.clean("a\u0000b.txt"));
        assertEquals(255, FileNames.clean("x".repeat(300)).length());
        assertThrows(ResponseStatusException.class, () -> FileNames.clean(".."));
        assertThrows(ResponseStatusException.class, () -> FileNames.clean("  "));
    }

    @Test
    void categoriesFromTypeOrExtension() {
        assertEquals(FileCategory.IMAGE, FileCategory.of("image/png", "a.png"));
        assertEquals(FileCategory.DOCUMENT, FileCategory.of("application/octet-stream", "report.DOCX"));
        assertEquals(FileCategory.ARCHIVE, FileCategory.of(null, "backup.zip"));
        assertEquals(FileCategory.CODE, FileCategory.of("text/plain", "Main.java"));
        assertEquals(FileCategory.OTHER, FileCategory.of(null, "noext"));
    }

    @Test
    void shareTokensAreRandomAndUrlSafe() {
        String a = ShareService.newToken();
        assertEquals(32, a.length());
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
        assertNotEquals(a, ShareService.newToken());
    }

    @Test
    void loginLockResetsOnSuccess() {
        var svc = new LoginAttemptService(2, 5);
        svc.failed("Bob");
        assertFalse(svc.isLocked("bob"));
        svc.succeeded("bob");
        svc.failed("bob");
        assertFalse(svc.isLocked("bob"));
        svc.failed("BOB");
        assertTrue(svc.isLocked("bob"));
    }
}
