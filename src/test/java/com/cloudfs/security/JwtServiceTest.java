package com.cloudfs.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    @Test
    void tokenRoundTripReturnsUsername() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        assertEquals("alice", jwt.extractUsername(jwt.generateToken("alice")));
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String token = new JwtService("another-secret-another-secret-12345", 60_000).generateToken("alice");
        assertThrows(JwtException.class, () -> new JwtService(SECRET, 60_000).extractUsername(token));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, -1_000);
        String token = jwt.generateToken("alice");
        assertThrows(JwtException.class, () -> jwt.extractUsername(token));
    }
}
