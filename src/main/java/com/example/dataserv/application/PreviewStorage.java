package com.example.dataserv.application;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component; 

@Component
public class PreviewStorage {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final Path baseDir;

    public PreviewStorage(Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createDirectories(baseDir);
    }

    public PreviewStorage() throws IOException {
        this(Path.of(System.getProperty("java.io.tmpdir"), "dataserv-previews"));
    }

    public UUID save(MultipartFile file) throws IOException {
        deleteExpired(DEFAULT_TTL);

        UUID id = UUID.randomUUID();
        Path target = targetPath(id);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return id;
    }

    public InputStream open(UUID id) throws IOException {
        Path target = targetPath(id);
        return Files.newInputStream(target);
    }

    public boolean exists(UUID id) {
        Path target = targetPath(id);
        return Files.exists(target);
    }

    public boolean isExpired(UUID id, Duration ttl) {
        Path target = targetPath(id);
        if (!Files.exists(target)) {
            return true;
        }

        try {
            long ageMillis = System.currentTimeMillis()
                    - Files.getLastModifiedTime(target).toMillis();
            return ageMillis > ttl.toMillis();
        } catch (IOException e) {
            return true;
        }
    }

    public void deleteExpired(Duration ttl) throws IOException {
        try (var stream = Files.list(baseDir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                UUID id = parseId(path);
                if (id != null && isExpired(id, ttl)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public void delete(UUID id) throws IOException {
        Path target = targetPath(id);
        Files.deleteIfExists(target);
    }

    private Path targetPath(UUID id) {
        return baseDir.resolve("preview_" + id + ".csv");
    }

    private UUID parseId(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.startsWith("preview_") || !fileName.endsWith(".csv")) {
            return null;
        }

        String uuidPart = fileName.substring("preview_".length(), fileName.length() - ".csv".length());

        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
