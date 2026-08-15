package com.example.dataserv.application;

import com.example.dataserv.api.DatasetValidationException;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.storage.DatasetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Integration-style tests for the preview -> create flow: CSV parsing,
 * PreviewStorage read/write/expiry, and wiring collectIssues/checkCanSubmit
 * results through to DatasetPreviewResponse and createDataset.
 *
 * Exhaustive validation-rule coverage (which column names/values trigger
 * which PreviewIssueKind, blocking vs non-blocking, etc.) lives in
 * SchemaValidationHelperTests - this file only needs enough cases to prove
 * the wiring is correct end-to-end, not to re-derive every rule.
 */
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
        assertEquals(2, response.getSchema().getColumns().size());
        assertFalse(response.getSampleRows().isEmpty());
        assertNotNull(response.getIssues());
    }

    @Test
    void previewLimitsSampleRowsToTen() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        StringBuilder csv = new StringBuilder("name,age\n");
        for (int i = 0; i < 25; i++) {
            csv.append("person").append(i).append(",").append(20 + i).append("\n");
        }

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", csv.toString().getBytes()
        );

        var response = service.previewDataset(file);

        assertEquals(10, response.getSampleRows().size());
    }

    @Test
    void previewRejectsEmptyFile() {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.csv", "text/csv", new byte[0]
        );

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.previewDataset(file)
        );

        assertTrue(ex.getMessage().contains("header"));
    }

    @Test
    void previewWiresCollectedIssuesAndCanSubmitFromSchema() throws Exception {
        // Sanity check that previewDataset actually plugs collectIssues/checkCanSubmit
        // output into the response, without re-testing every validation rule here.
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", ",age\nAlice,25\nBob,30\n".getBytes()
        );

        var response = service.previewDataset(file);

        assertFalse(response.isCanSubmit());
        assertFalse(response.getIssues().isEmpty());
    }

    @Test
    void createDatasetFromPreviewRejectsInvalidPreview() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", ",age\nAlice,25\nBob,30\n".getBytes()
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
            "file", "data.csv", "text/csv", "name,age\nAlice,25\nBob,30\n".getBytes()
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
    void createDatasetFromPreviewRejectsUnknownPreviewId() {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        java.util.UUID unknownId = java.util.UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.createDatasetFromPreview("nope", unknownId)
        );

        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void createDatasetFromPreviewRejectsExpiredPreview() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetService service = new DatasetService(repository);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", "name,age\nAlice,25\n".getBytes()
        );

        var preview = service.previewDataset(file);
        PreviewStorage storage = new PreviewStorage();
        Path target = Path.of(
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