package com.example.dataserv.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreviewStorageTests {

    @TempDir
    Path tempDir;

    @Test
    void saveAndOpenAndDelete() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "name,age\nAlice,25\nBob,30\n".getBytes()
        );

        UUID id = storage.save(file);

        // verify the saved preview can be found and opened before deletion
        assertTrue(storage.exists(id));

        try (InputStream in = storage.open(id)) {
            assertNotNull(in);
            byte[] buf = new byte[4];
            int r = in.read(buf);
            assertTrue(r > 0);
        }

        storage.delete(id);

        // deletion should remove the preview from storage
        assertFalse(storage.exists(id));
    }

    @Test
    void expiredPreviewIsDetectedAndDeleted() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "expired.csv",
            "text/csv",
            "name,age\nAlice,25\n".getBytes()
        );

        UUID id = storage.save(file);
        Path target = tempDir.resolve("preview_" + id + ".csv");

        // backdate the file so it is unambiguously past the expiry threshold
        Files.setLastModifiedTime(
            target,
            FileTime.fromMillis(
                System.currentTimeMillis() - Duration.ofHours(1).toMillis()
            )
        );

        assertTrue(storage.isExpired(id, Duration.ofMinutes(30)));

        storage.deleteExpired(Duration.ofMinutes(30));

        // expired previews should be removed during cleanup.
        assertFalse(storage.exists(id));
    }

    @Test
    void saveCleansUpExpiredPreviewsBeforeCreatingNewOne() throws Exception {
        PreviewStorage storage = new PreviewStorage(tempDir);

        UUID expiredId = UUID.randomUUID();
        Path expiredTarget = tempDir.resolve("preview_" + expiredId + ".csv");

        Files.writeString(
            expiredTarget,
            "name,age\nAlice,25\n"
        );

        // create an existing preview that is already past the cleanup threshold
        Files.setLastModifiedTime(
            expiredTarget,
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

        UUID newId = storage.save(newFile);

        // saving a new preview should clean up stale files without affecting the new one
        assertFalse(storage.exists(expiredId));
        assertTrue(storage.exists(newId));
    }
}