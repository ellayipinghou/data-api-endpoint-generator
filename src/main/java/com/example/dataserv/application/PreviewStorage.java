package com.example.dataserv.application;

import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

    public PreviewStorage(Path baseDir, ObjectMapper objectMapper) throws IOException {
        this.baseDir = baseDir;
        this.objectMapper = objectMapper;
        Files.createDirectories(baseDir);
    }

    public PreviewStorage() throws IOException {
        this(
            Path.of(System.getProperty("java.io.tmpdir"), "dataserv-previews"),
            new ObjectMapper()
        );
    }

    public UUID save(MultipartFile file, PreviewMetadata metadata) throws IOException {
        // remove expired previews before saving a new one
        deleteExpired(DEFAULT_TTL);

        UUID id = UUID.randomUUID();

        Path csvTarget = baseDir.resolve("preview_" + id + ".csv");
        Path metadataTarget = baseDir.resolve("preview_" + id + ".json");

        try {
            // write the uploaded file
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, csvTarget, StandardCopyOption.REPLACE_EXISTING);
            }

            // write the schema and type options metadata
            objectMapper.writeValue(metadataTarget.toFile(), metadata);

            return id;
        } catch (IOException e) {
            // remove partial files if saving either component fails
            Files.deleteIfExists(csvTarget);
            Files.deleteIfExists(metadataTarget);
            throw e;
        }
    }

    public InputStream open(UUID id) throws IOException {
        Path target = baseDir.resolve("preview_" + id + ".csv");
        return Files.newInputStream(target);
    }

    public PreviewMetadata openMetadata(UUID id) throws IOException {
        Path target = baseDir.resolve("preview_" + id + ".json");
        return objectMapper.readValue(target.toFile(), PreviewMetadata.class);
    }

    public boolean exists(UUID id) {
        Path csv = baseDir.resolve("preview_" + id + ".csv");
        Path metadata = baseDir.resolve("preview_" + id + ".json");

        // a preview is valid only when both components exist
        return Files.exists(csv) && Files.exists(metadata);
    }

    public boolean isExpired(UUID id, Duration ttl) {
        Path target = baseDir.resolve("preview_" + id + ".csv");

        // missing previews are treated as expired
        if (!Files.exists(target)) {
            return true;
        }

        try {
            // use the csv timestamp as the preview's expiration timestamp
            long ageMillis =
                System.currentTimeMillis() - Files.getLastModifiedTime(target).toMillis();

            return ageMillis > ttl.toMillis();
        } catch (IOException e) {
            // treat unreadable timestamps as expired
            return true;
        }
    }

    public void deleteExpired(Duration ttl) throws IOException {
        try (var stream = Files.list(baseDir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                UUID id = parseId(path);

                // delete the entire preview when its csv has expired
                if (id != null && isExpired(id, ttl)) {
                    delete(id);
                }
            }
        }
    }

    public void delete(UUID id) throws IOException {
        // delete both the uploaded file and its metadata
        Files.deleteIfExists(baseDir.resolve("preview_" + id + ".csv"));
        Files.deleteIfExists(baseDir.resolve("preview_" + id + ".json"));
    }

    private UUID parseId(Path path) {
        String fileName = path.getFileName().toString();

        // only csv files are used to identify previews for expiration
        if (!fileName.startsWith("preview_") || !fileName.endsWith(".csv")) {
            return null;
        }

        String uuidPart = fileName.substring(
            "preview_".length(),
            fileName.length() - ".csv".length()
        );

        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
