package com.example.dataserv.application;

import com.example.dataserv.api.DatasetValidationException;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.storage.DatasetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DatasetServicePreviewTests {

    @Test
    void previewReturnsSchemaAndSamples() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);

        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "name,age\nAlice,25\nBob,30\nCharlie,35\n".getBytes()
        );

        var response = service.previewDataset(file);

        assertNotNull(response);
        assertNotNull(response.getPreviewId());
        assertNotNull(response.getSchema());
        assertFalse(response.getSchema().getColumns().isEmpty());
        assertFalse(response.getSampleRows().isEmpty());
        assertNotNull(response.getIssues());
        assertTrue(response.isCanSubmit());
    }

    @Test
    void previewBlocksSubmitWhenHeaderIsBlank() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                ",age\nAlice,25\nBob,30\n".getBytes()
        );

        var response = service.previewDataset(file);

        assertFalse(response.isCanSubmit());
        assertTrue(response.getIssues().stream()
                .anyMatch(issue -> issue.getKind().equals("EMPTY_NAME")));
    }

    @Test
    void previewFlagsHeaderThatLooksLikeData() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "1,2\nAlice,25\nBob,30\n".getBytes()
        );

        var response = service.previewDataset(file);

        // HEADER_SUSPECTED_DATA_ROW is now a non-blocking warning
        assertTrue(response.isCanSubmit());
        assertTrue(response.getIssues().stream()
                .anyMatch(issue -> issue.getKind().equals("HEADER_SUSPECTED_DATA_ROW") && !issue.getKind().isBlocking()));
    }

    @Test
    void createDatasetFromPreviewRejectsInvalidPreview() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                ",age\nAlice,25\nBob,30\n".getBytes()
        );

        var preview = service.previewDataset(file);

        DatasetValidationException ex = assertThrows(
                DatasetValidationException.class,
                () -> service.createDatasetFromPreview("invalid", preview.getPreviewId())
        );

        assertTrue(ex.getMessage().contains("validation"));
    }

    @Test
    void createDatasetFromPreviewUsesStoredCsvAndSchema() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "name,age\nAlice,25\nBob,30\n".getBytes()
        );

        var preview = service.previewDataset(file);
        Dataset created = service.createDatasetFromPreview("from-preview", preview.getPreviewId());

        assertNotNull(created);
        assertEquals("from-preview", created.getName());
        assertEquals(2, created.getSchema().getColumns().size());
        assertFalse(new PreviewStorage().exists(preview.getPreviewId()));
        verify(repository).saveMetadata(created);
        verify(repository).createTable(eq(created.getId()), eq(created.getSchema()));
        verify(repository).copyData(eq(created.getId()), eq(created.getSchema()), any());
    }

    @Test
    void createDatasetFromPreviewRejectsExpiredPreview() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "name,age\nAlice,25\n".getBytes()
        );

        var preview = service.previewDataset(file);
        PreviewStorage storage = new PreviewStorage();
        Path target = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"),
                "dataserv-previews",
                "preview_" + preview.getPreviewId() + ".csv"
        );
        java.nio.file.Files.setLastModifiedTime(
                target,
                java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis() - java.time.Duration.ofHours(1).toMillis()
                )
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createDatasetFromPreview("expired", preview.getPreviewId())
        );

        assertTrue(ex.getMessage().contains("expired"));
        assertFalse(storage.exists(preview.getPreviewId()));
    }
}
