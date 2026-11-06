package com.cloudfs.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Chong do mat khau: khoa tam username sau N lan sai lien tiep.
 * Luu trong RAM (moi instance rieng) - du cho 1 node; nhieu node thi chuyen sang Redis.
 */
@Service
public class LoginAttemptService {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttemptService(@Value("${app.login.max-failures:5}") int maxFailures,
                               @Value("${app.login.lock-minutes:5}") long lockMinutes) {
        this.maxFailures = maxFailures;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(String username) {
        Attempts a = attempts.get(key(username));
        return a != null && a.lockedUntil() != null && Instant.now().isBefore(a.lockedUntil());
    }

    public void failed(String username) {
        attempts.compute(key(username), (k, a) -> {
            int n = (a == null || a.lockedUntil() != null) ? 1 : a.failures() + 1;
            return n >= maxFailures ? new Attempts(n, Instant.now().plus(lockDuration)) : new Attempts(n, null);
        });
    }

    public void succeeded(String username) {
        attempts.remove(key(username));
    }
}
