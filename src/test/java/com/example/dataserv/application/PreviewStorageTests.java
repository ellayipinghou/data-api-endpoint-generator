package com.example.dataserv.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreviewStorageTests {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PreviewMetadata createMetadata() {
        return new PreviewMetadata(
            null,
            Map.of(
                "name", List.of(),
                "age", List.of()
            )
        );
    }

    @Test
    void saveAndOpenAndDelete() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir, objectMapper);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "name,age\nAlice,25\nBob,30\n".getBytes()
        );

        UUID id = storage.save(file, createMetadata());

        // verify that both preview components exist after saving
        assertTrue(Files.exists(tempDir.resolve("preview_" + id + ".csv")));
        assertTrue(Files.exists(tempDir.resolve("preview_" + id + ".json")));
        assertTrue(storage.exists(id));

        // verify that the csv can be opened
        try (InputStream in = storage.open(id)) {
            assertNotNull(in);
            assertEquals(
                "name,age\nAlice,25\nBob,30\n",
                new String(in.readAllBytes())
            );
        }

        storage.delete(id);

        // deleting a preview should remove both components
        assertFalse(Files.exists(tempDir.resolve("preview_" + id + ".csv")));
        assertFalse(Files.exists(tempDir.resolve("preview_" + id + ".json")));
        assertFalse(storage.exists(id));
    }

    @Test
    void saveAndOpenMetadata() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir, objectMapper);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "name,age\nAlice,25\n".getBytes()
        );

        PreviewMetadata metadata = createMetadata();

        UUID id = storage.save(file, metadata);

        PreviewMetadata stored = storage.openMetadata(id);

        // verify that metadata survives serialization and deserialization
        assertNotNull(stored);
        assertEquals(metadata.schema(), stored.schema());
        assertEquals(metadata.typeOptions(), stored.typeOptions());
    }

    @Test
    void existsReturnsFalseWhenCsvIsMissing() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir, objectMapper);

        UUID id = UUID.randomUUID();

        // create only the metadata component
        Path metadata = tempDir.resolve("preview_" + id + ".json");
        objectMapper.writeValue(metadata.toFile(), createMetadata());

        // a preview is valid only when both components exist
        assertFalse(storage.exists(id));
    }

    @Test
    void existsReturnsFalseWhenMetadataIsMissing() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir, objectMapper);

        UUID id = UUID.randomUUID();

        // create only the csv component
        Path csv = tempDir.resolve("preview_" + id + ".csv");
        Files.writeString(csv, "name,age\nAlice,25\n");

        // a preview is valid only when both components exist
        assertFalse(storage.exists(id));
    }

    @Test
    void expiredPreviewIsDetectedAndDeleted() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir, objectMapper);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "expired.csv",
            "text/csv",
            "name,age\nAlice,25\n".getBytes()
        );

        UUID id = storage.save(file, createMetadata());

        Path csv = tempDir.resolve("preview_" + id + ".csv");
        Path metadata = tempDir.resolve("preview_" + id + ".json");

        // backdate the csv so it is unambiguously past the expiry threshold
        Files.setLastModifiedTime(
            csv,
            FileTime.fromMillis(
                System.currentTimeMillis() - Duration.ofHours(1).toMillis()
            )
        );

        assertTrue(storage.isExpired(id, Duration.ofMinutes(30)));

        storage.deleteExpired(Duration.ofMinutes(30));

        // expiration should remove both preview components
        assertFalse(Files.exists(csv));
        assertFalse(Files.exists(metadata));
        assertFalse(storage.exists(id));
    }

    @Test
    void saveCleansUpExpiredPreviewsBeforeCreatingNewOne() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir, objectMapper);

        UUID expiredId = UUID.randomUUID();
        Path expiredCsv = tempDir.resolve("preview_" + expiredId + ".csv");
        Path expiredMetadata = tempDir.resolve("preview_" + expiredId + ".json");

        Files.writeString(
            expiredCsv,
            "name,age\nAlice,25\n"
        );
        objectMapper.writeValue(
            expiredMetadata.toFile(),
            createMetadata()
        );

        // make the existing preview older than the cleanup threshold
        Files.setLastModifiedTime(
            expiredCsv,
            FileTime.fromMillis(
                System.currentTimeMillis() - Duration.ofHours(1).toMillis()
            )
        );

        MockMultipartFile newFile = new MockMultipartFile(
            "file",
            "fresh.csv",
            "text/csv",
            "name,age\nBob,30\n".getBytes()
        );

        UUID newId = storage.save(newFile, createMetadata());

        // saving a new preview should remove both components of stale previews
        assertFalse(Files.exists(expiredCsv));
        assertFalse(Files.exists(expiredMetadata));
        assertFalse(storage.exists(expiredId));

        // the new preview should remain intact
        assertTrue(storage.exists(newId));
    }
}
