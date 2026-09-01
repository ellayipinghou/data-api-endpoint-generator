package com.example.dataserv.application;

import com.example.dataserv.api.DatasetValidationException;
import com.example.dataserv.api.InvalidTypeOverrideException;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.ingestion.DatasetParser;
import com.example.dataserv.ingestion.csv.CsvDatasetParser;
import com.example.dataserv.storage.DatasetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

/**
 * integration-style tests for the preview -> create flow: csv parsing,
 * previewstorage persistence/expiry, and wiring validation results through
 * datasetpreviewresponse and createdataset.
 *
 * most tests use a real previewstorage backed by a per-test temporary
 * directory because the preview -> create flow depends on actual stored
 * files. tests that do not exercise storage mock it instead.
 *
 * exhaustive validation-rule coverage (which column names/values trigger
 * which previewissuekind, blocking vs non-blocking, etc.) lives in
 * schemavalidationhelpertests - this file only needs enough cases to prove
 * the wiring is correct end-to-end, not to re-derive every rule.
 */
@ExtendWith(MockitoExtension.class)
class DatasetServicePreviewTests {

    // isolate preview files so tests do not share or modify application storage
    @TempDir
    Path tempDir;

    @Test
    void previewReturnsSchemaAndSamples() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage because previewDataset must persist the uploaded file
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "data.csv",
            "text/csv",
            "name,age\nAlice,25\nBob,30\nCharlie,35\n".getBytes()
        );

        var response = service.previewDataset(file);

        // verify that parsing and preview response data were populated
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
        DatasetParser parser = new CsvDatasetParser();

        // use real storage because previewDataset persists the uploaded file
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        StringBuilder csv = new StringBuilder("name,age\n");
        for (int i = 0; i < 25; i++) {
            csv.append("person").append(i).append(",").append(20 + i).append("\n");
        }

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", csv.toString().getBytes()
        );

        var response = service.previewDataset(file);

        // confirm that only the configured maximum number of sample rows is returned
        assertEquals(10, response.getSampleRows().size());
    }

    @Test
    void previewRejectsEmptyFile() {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // mock storage because parsing fails before preview storage is accessed
        PreviewStorage storage = mock(PreviewStorage.class);
        DatasetService service = new DatasetService(repository, parser, storage);

        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.csv", "text/csv", new byte[0]
        );

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.previewDataset(file)
        );

        // the parser should reject the file because it has no header
        assertTrue(ex.getMessage().contains("header"));
    }

    @Test
    void previewWiresCollectedIssuesAndCanSubmitFromSchema() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage because this test exercises the full preview flow
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", ",age\nAlice,25\nBob,30\n".getBytes()
        );

        var response = service.previewDataset(file);

        // the blank column should produce an issue that prevents submission
        assertFalse(response.isCanSubmit());
        assertFalse(response.getIssues().isEmpty());
    }

    @Test
    void createDatasetFromPreviewRejectsInvalidPreview() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage because createDatasetFromPreview reads the stored preview
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", ",age\nAlice,25\nBob,30\n".getBytes()
        );

        var preview = service.previewDataset(file);

        // the preview exists, but its schema contains a blocking validation issue
        DatasetValidationException ex = assertThrows(
            DatasetValidationException.class,
            () -> service.createDatasetFromPreview("invalid", preview.getPreviewId(), Map.of())
        );

        assertTrue(ex.getMessage().contains("validation"));
    }

    @Test
    void createDatasetFromPreviewUsesStoredCsvAndSchema() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage to verify the preview is read from persisted csv data
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", "name,age\nAlice,25\nBob,30\n".getBytes()
        );

        var preview = service.previewDataset(file);
        Dataset created = service.createDatasetFromPreview(
            "from-preview", preview.getPreviewId(), Map.of()
        );

        assertNotNull(created);
        assertEquals("from-preview", created.getName());
        assertEquals(2, created.getSchema().getColumns().size());

        // successful creation should remove the temporary preview file
        assertFalse(storage.exists(preview.getPreviewId()));

        // verify that the repository received the created dataset and schema
        verify(repository).saveMetadata(created);
        verify(repository).createTable(eq(created.getId()), eq(created.getSchema()));
        verify(repository).copyData(eq(created.getId()), eq(created.getSchema()), any());
    }

    @Test
    void createDatasetFromPreviewRejectsUnknownPreviewId() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage so the missing-id check exercises actual storage behavior
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        java.util.UUID unknownId = java.util.UUID.randomUUID();

        PreviewMissingException ex = assertThrows(
            PreviewMissingException.class,
            () -> service.createDatasetFromPreview("nope", unknownId, Map.of())
        );

        // an id with no corresponding preview file should be rejected immediately
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void createDatasetFromPreviewRejectsExpiredPreview() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage because expiration depends on the stored file timestamp
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", "name,age\nAlice,25\n".getBytes()
        );

        var preview = service.previewDataset(file);

        Path target = tempDir.resolve(
            "preview_" + preview.getPreviewId() + ".csv"
        );

        // make the preview older than the configured 30-minute ttl
        java.nio.file.Files.setLastModifiedTime(
            target,
            java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() - java.time.Duration.ofHours(1).toMillis()
            )
        );

        PreviewMissingException ex = assertThrows(
            PreviewMissingException.class,
            () -> service.createDatasetFromPreview("expired", preview.getPreviewId(), Map.of())
        );

        assertTrue(ex.getMessage().contains("expired"));

        // expired previews should be deleted when they are detected
        assertFalse(storage.exists(preview.getPreviewId()));
    }

    @Test
    void createDatasetFromPreviewAppliesValidTypeOverride() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage because the override is applied after reading the stored preview
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        // all sampled age values support both integer and string representations
        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", "name,age\nAlice,25\nBob,30\n".getBytes()
        );

        var preview = service.previewDataset(file);
        Dataset created = service.createDatasetFromPreview(
            "with-override", preview.getPreviewId(), Map.of("age", DataType.STRING)
        );

        var ageColumn = created.getSchema().getColumns().stream()
            .filter(c -> c.getName().equals("age"))
            .findFirst()
            .orElseThrow();

        // confirm that the requested type override was applied to the created schema
        assertEquals(DataType.STRING, ageColumn.getType());
    }

    @Test
    void createDatasetFromPreviewRejectsOverrideOutsideSampledCandidates() throws Exception {
        DatasetRepository repository = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();

        // use real storage because type candidates come from the stored preview
        PreviewStorage storage = new PreviewStorage(tempDir);
        DatasetService service = new DatasetService(repository, parser, storage);

        MockMultipartFile file = new MockMultipartFile(
            "file", "data.csv", "text/csv", "name,age\nAlice,25\nBob,30\n".getBytes()
        );

        var preview = service.previewDataset(file);

        // date is not a valid inferred candidate for the sampled integer values
        InvalidTypeOverrideException ex = assertThrows(
            InvalidTypeOverrideException.class,
            () -> service.createDatasetFromPreview(
                "bad-override",
                preview.getPreviewId(),
                Map.of("age", DataType.DATE)
            )
        );

        assertTrue(ex.getMessage().contains("age"));
    }
}
