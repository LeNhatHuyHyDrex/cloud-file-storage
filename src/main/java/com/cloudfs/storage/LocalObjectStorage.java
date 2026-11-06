package com.cloudfs.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

/** Luu object thanh file tren dia. Chi dung cho dev/test khi khong co S3. */
public class LocalObjectStorage implements ObjectStorage {

    private final Path root;

    public LocalObjectStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return p;
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) throws IOException {
        Path p = resolve(key);
        Files.createDirectories(p.getParent());
        Files.copy(content, p, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream get(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<URL> presignDownload(String key, String fileName, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public String backendName() {
        return "local";
    }
}
