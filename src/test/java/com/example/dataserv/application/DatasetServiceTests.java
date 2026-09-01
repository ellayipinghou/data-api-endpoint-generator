package com.example.dataserv.application;

import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.domain.Filter;
import com.example.dataserv.domain.FilterOperator;
import com.example.dataserv.ingestion.DatasetParser;
import com.example.dataserv.ingestion.csv.CsvDatasetParser;
import com.example.dataserv.storage.DatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTests {

    @Mock
    private DatasetRepository repository;

    private DatasetService service;
    private DatasetParser parser;
    private PreviewStorage previewStorage;

    @BeforeEach
    void setUp() {
        parser = new CsvDatasetParser();

        // preview storage is not used by the methods covered in this test class
        service = new DatasetService(repository, parser, previewStorage);
    }

    /*
     * =========================
     * create tests
     * =========================
     */

    @Test
    void createDatasetCreatesAndReturnsDataset() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = new ByteArrayInputStream("""
                name,age
                Alice,25
                Bob,30
                """.getBytes());

        Dataset result = service.createDataset("test-dataset", schema, input);

        // verify that the service creates the expected dataset metadata
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("test-dataset", result.getName());
        assertEquals(schema, result.getSchema());

        // capture the generated dataset id so the same id can be checked on later calls
        org.mockito.ArgumentCaptor<Dataset> captor =
            org.mockito.ArgumentCaptor.forClass(Dataset.class);
        verify(repository).saveMetadata(captor.capture());

        java.util.UUID savedId = captor.getValue().getId();

        // verify that the generated id and schema are passed through to storage
        verify(repository).createTable(savedId, schema);
        verify(repository).copyData(savedId, schema, input);
    }

    @Test
    void createDatasetCallsRepositoryMethodsInCorrectOrder() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = new ByteArrayInputStream(
            "name,age\nAlice,25\n".getBytes()
        );

        service.createDataset("test-dataset", schema, input);

        InOrder inOrder = inOrder(repository);

        // metadata must exist before the table and data are created
        inOrder.verify(repository).saveMetadata(any(Dataset.class));
        inOrder.verify(repository).createTable(
            any(java.util.UUID.class),
            eq(schema)
        );
        inOrder.verify(repository).copyData(
            any(java.util.UUID.class),
            eq(schema),
            eq(input)
        );
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void createDatasetPassesCorrectDatasetToRepository() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = new ByteArrayInputStream(
            "name,age\nAlice,25\n".getBytes()
        );

        service.createDataset("my-dataset", schema, input);

        org.mockito.ArgumentCaptor<Dataset> captor =
            org.mockito.ArgumentCaptor.forClass(Dataset.class);
        verify(repository).saveMetadata(captor.capture());

        Dataset saved = captor.getValue();

        // verify that repository metadata matches the requested dataset
        assertEquals("my-dataset", saved.getName());
        assertEquals(schema, saved.getSchema());
        assertNotNull(saved.getId());
    }

    @Test
    void createDatasetPropagatesIOException() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = mock(InputStream.class);
        IOException exception = new IOException("Failed to read input");

        // repository.copyData is responsible for reading the input stream
        doThrow(exception)
            .when(repository)
            .copyData(
                any(java.util.UUID.class),
                eq(schema),
                eq(input)
            );

        IOException thrown =
            assertThrows(
                IOException.class,
                () -> service.createDataset("test-dataset", schema, input)
            );

        // the original repository exception should be propagated unchanged
        assertSame(exception, thrown);
        verify(repository).saveMetadata(any(Dataset.class));
        verify(repository).createTable(
            any(java.util.UUID.class),
            eq(schema)
        );
        verify(repository).copyData(
            any(java.util.UUID.class),
            eq(schema),
            eq(input)
        );
    }

    @Test
    void createDatasetPropagatesSQLException() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = new ByteArrayInputStream(
            "name,age\nAlice,25\n".getBytes()
        );
        SQLException exception = new SQLException("Database error");

        // fail during table creation to verify later repository calls are skipped
        doThrow(exception)
            .when(repository)
            .createTable(
                any(java.util.UUID.class),
                eq(schema)
            );

        SQLException thrown =
            assertThrows(
                SQLException.class,
                () -> service.createDataset("test-dataset", schema, input)
            );

        // the original repository exception should be propagated unchanged
        assertSame(exception, thrown);
        verify(repository).saveMetadata(any(Dataset.class));
        verify(repository).createTable(
            any(java.util.UUID.class),
            eq(schema)
        );

        // data should not be loaded when table creation fails
        verify(repository, never()).copyData(any(), any(), any());
    }

    /*
     * =========================
     * find tests
     * =========================
     */

    @Test
    void findDatasetReturnsDatasetWhenRepositoryFindsIt() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        Optional<Dataset> result = service.findDataset(id);

        // verify that the same dataset returned by the repository is exposed
        assertTrue(result.isPresent());
        assertSame(dataset, result.get());
        verify(repository).findById(id);
    }

    @Test
    void findDatasetReturnsEmptyWhenRepositoryDoesNotFindIt() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id)).thenReturn(Optional.empty());

        Optional<Dataset> result = service.findDataset(id);

        // a missing repository result should remain an empty optional
        assertTrue(result.isEmpty());
        verify(repository).findById(id);
    }

    /*
     * =========================
     * delete tests
     * =========================
     */

    @Test
    void deleteDatasetDelegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.deleteDataset(id);

        // deletion is delegated directly to the repository
        verify(repository).deleteById(id);
    }

    @Test
    void deleteDatasetDoesNotCallOtherRepositoryMethods() throws Exception {
        UUID id = UUID.randomUUID();

        service.deleteDataset(id);

        // verify that delete does not trigger unrelated repository operations
        verify(repository).deleteById(id);
        verify(repository, never()).saveMetadata(any());
        verify(repository, never()).createTable(any(), any());
        verify(repository, never()).copyData(any(), any(), any());
        verify(repository, never()).findById(any());
        verify(repository, never()).query(any(), any());
    }

    /*
     * =========================
     * query tests
     * =========================
     */

    @Test
    void queryDatasetReturnsResultsFromRepository() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        List<DataRow> expectedResults = List.of(
            new DataRow(java.util.Map.of("name", "Alice", "age", 25)),
            new DataRow(java.util.Map.of("name", "Bob", "age", 30))
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(expectedResults);

        List<DataRow> results = service.queryDataset(id, List.of());

        // verify that repository results are returned without modification
        assertEquals(expectedResults, results);
        verify(repository).findById(id);
        verify(repository).query(id, List.of());
    }

    @Test
    void queryDatasetThrowsWhenDatasetDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id)).thenReturn(Optional.empty());

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(id, List.of())
            );

        // querying a missing dataset should fail before reaching the repository query
        assertEquals("Dataset not found: " + id, exception.getMessage());
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetAllowsNoFilters() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );
        List<DataRow> expectedResults = List.of();

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(id, List.of())).thenReturn(expectedResults);

        List<DataRow> results = service.queryDataset(id, List.of());

        // an empty filter list should be passed through unchanged
        assertTrue(results.isEmpty());
        verify(repository).findById(id);
        verify(repository).query(id, List.of());
    }

    @Test
    void queryDatasetConvertsIntegerFilterValue() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        Filter filter = new Filter(
            "age",
            FilterOperator.GREATER_THAN,
            "25"
        );

        service.queryDataset(id, List.of(filter));

        // verify that the string input is converted to an integer before querying
        verify(repository).query(
            id,
            List.of(
                new Filter(
                    "age",
                    FilterOperator.GREATER_THAN,
                    25
                )
            )
        );
    }

    @Test
    void queryDatasetConvertsMultipleFilterValues() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        List<Filter> filters = List.of(
            new Filter("name", FilterOperator.EQUALS, "Alice"),
            new Filter("age", FilterOperator.GREATER_THAN, "25")
        );

        service.queryDataset(id, filters);

        // verify that each filter is validated and converted independently
        verify(repository).query(
            id,
            List.of(
                new Filter(
                    "name",
                    FilterOperator.EQUALS,
                    "Alice"
                ),
                new Filter(
                    "age",
                    FilterOperator.GREATER_THAN,
                    25
                )
            )
        );
    }

    @Test
    void queryDatasetConvertsLongValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("id", DataType.LONG)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        service.queryDataset(
            id,
            List.of(
                new Filter(
                    "id",
                    FilterOperator.EQUALS,
                    "123456789"
                )
            )
        );

        // verify conversion from the filter's string value to long
        verify(repository).query(
            id,
            List.of(
                new Filter(
                    "id",
                    FilterOperator.EQUALS,
                    123456789L
                )
            )
        );
    }

    @Test
    void queryDatasetConvertsDoubleValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("score", DataType.DOUBLE)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        service.queryDataset(
            id,
            List.of(
                new Filter(
                    "score",
                    FilterOperator.GREATER_THAN,
                    "95.5"
                )
            )
        );

        // verify conversion from the filter's string value to double
        verify(repository).query(
            id,
            List.of(
                new Filter(
                    "score",
                    FilterOperator.GREATER_THAN,
                    95.5
                )
            )
        );
    }

    @Test
    void queryDatasetConvertsBooleanValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("active", DataType.BOOLEAN)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        service.queryDataset(
            id,
            List.of(
                new Filter(
                    "active",
                    FilterOperator.EQUALS,
                    "true"
                )
            )
        );

        // verify case-insensitive boolean conversion to the expected type
        verify(repository).query(
            id,
            List.of(
                new Filter(
                    "active",
                    FilterOperator.EQUALS,
                    true
                )
            )
        );
    }

    @Test
    void queryDatasetConvertsDateValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("birthday", DataType.DATE)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        service.queryDataset(
            id,
            List.of(
                new Filter(
                    "birthday",
                    FilterOperator.GREATER_THAN,
                    "2020-01-01"
                )
            )
        );

        // verify that the ISO date string becomes a LocalDate
        verify(repository).query(
            id,
            List.of(
                new Filter(
                    "birthday",
                    FilterOperator.GREATER_THAN,
                    LocalDate.of(2020, 1, 1)
                )
            )
        );
    }

    @Test
    void queryDatasetConvertsDateTimeValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("created", DataType.DATETIME)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        service.queryDataset(
            id,
            List.of(
                new Filter(
                    "created",
                    FilterOperator.GREATER_THAN,
                    "2025-01-01T12:30:00"
                )
            )
        );

        // verify that the ISO datetime string becomes a LocalDateTime
        verify(repository).query(
            id,
            List.of(
                new Filter(
                    "created",
                    FilterOperator.GREATER_THAN,
                    LocalDateTime.of(2025, 1, 1, 12, 30)
                )
            )
        );
    }

    @Test
    void queryDatasetRejectsUnknownColumn() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(
                        new Filter(
                            "does_not_exist",
                            FilterOperator.EQUALS,
                            "Alice"
                        )
                    )
                )
            );

        // validation should stop the query before it reaches the repository
        assertEquals(
            "Unknown column: does_not_exist",
            exception.getMessage()
        );
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsNullOperator() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(
                        new Filter(
                            "age",
                            null,
                            "25"
                        )
                    )
                )
            );

        // a null operator is invalid and must prevent the repository query
        assertEquals(
            "Filter operator must not be null",
            exception.getMessage()
        );
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsNullFilterValue() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(
                        new Filter(
                            "age",
                            FilterOperator.EQUALS,
                            null
                        )
                    )
                )
            );

        // a null value cannot be converted to the column's data type
        assertEquals(
            "Filter value must not be null",
            exception.getMessage()
        );
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsInvalidIntegerValue() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(
                        new Filter(
                            "age",
                            FilterOperator.EQUALS,
                            "not-an-integer"
                        )
                    )
                )
            );

        // invalid numeric input should fail before the repository is queried
        assertTrue(exception.getMessage().contains("not-an-integer"));
        assertTrue(exception.getMessage().contains("INTEGER"));
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsInvalidDoubleValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("score", DataType.DOUBLE)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.queryDataset(
                id,
                List.of(
                    new Filter(
                        "score",
                        FilterOperator.EQUALS,
                        "not-a-double"
                    )
                )
            )
        );

        // invalid numeric input should prevent any database query
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsInvalidBooleanValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("active", DataType.BOOLEAN)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(
                        new Filter(
                            "active",
                            FilterOperator.EQUALS,
                            "yes"
                        )
                    )
                )
            );

        // only true and false are valid boolean filter values
        assertTrue(
            exception.getMessage().contains(
                "Value 'yes' is not valid for type BOOLEAN"
            )
        );
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsInvalidDateValue() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("birthday", DataType.DATE)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.queryDataset(
                id,
                List.of(
                    new Filter(
                        "birthday",
                        FilterOperator.EQUALS,
                        "not-a-date"
                    )
                )
            )
        );

        // invalid date input should prevent any database query
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsOrderedComparisonOnBoolean() {
        UUID id = UUID.randomUUID();
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("active", DataType.BOOLEAN)
        ));
        Dataset dataset = new Dataset(id, "test-dataset", schema);

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(
                        new Filter(
                            "active",
                            FilterOperator.GREATER_THAN,
                            "true"
                        )
                    )
                )
            );

        // ordered comparisons are not valid for boolean columns
        assertTrue(
            exception.getMessage().contains(
                "not supported for BOOLEAN"
            )
        );
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetDoesNotCallRepositoryQueryWhenValidationFails() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testSchema()
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.queryDataset(
                id,
                List.of(
                    new Filter(
                        "does_not_exist",
                        FilterOperator.EQUALS,
                        "Alice"
                    )
                )
            )
        );

        // validation failure must prevent execution of the database query
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    /*
     * =========================
     * helpers
     * =========================
     */

    private DatasetSchema testSchema() {
        return new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("age", DataType.INTEGER)
        ));
    }
}
