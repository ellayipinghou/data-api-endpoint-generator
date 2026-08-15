package com.example.dataserv.application;

import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.domain.Filter;
import com.example.dataserv.domain.FilterOperator;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTests {

    @Mock
    private DatasetRepository repository;

    private DatasetService service;

    @BeforeEach
    void setUp() {
        service = new DatasetService(repository);
    }

    /*
     * =========================
     * Create tests
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

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("test-dataset", result.getName());
        assertEquals(schema, result.getSchema());

        // capture saved dataset id
        org.mockito.ArgumentCaptor<Dataset> captor =
            org.mockito.ArgumentCaptor.forClass(Dataset.class);
        verify(repository).saveMetadata(captor.capture());
        java.util.UUID savedId = captor.getValue().getId();

        verify(repository).createTable(savedId, schema);
        verify(repository).copyData(savedId, schema, input);
    }

    @Test
    void createDatasetCallsRepositoryMethodsInCorrectOrder() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = new ByteArrayInputStream("name,age\nAlice,25\n".getBytes());

        service.createDataset("test-dataset", schema, input);

        InOrder inOrder = inOrder(repository);

        inOrder.verify(repository).saveMetadata(any(Dataset.class));
        inOrder.verify(repository).createTable(any(java.util.UUID.class), eq(schema));
        inOrder.verify(repository).copyData(any(java.util.UUID.class), eq(schema), eq(input));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void createDatasetPassesCorrectDatasetToRepository() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = new ByteArrayInputStream("name,age\nAlice,25\n".getBytes());

        service.createDataset("my-dataset", schema, input);

        org.mockito.ArgumentCaptor<Dataset> captor =
            org.mockito.ArgumentCaptor.forClass(Dataset.class);
        verify(repository).saveMetadata(captor.capture());

        Dataset saved = captor.getValue();
        assertEquals("my-dataset", saved.getName());
        assertEquals(schema, saved.getSchema());
        assertNotNull(saved.getId());
    }

    @Test
    void createDatasetPropagatesIOException() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = mock(InputStream.class);
        IOException exception = new IOException("Failed to read input");

        // repository.copyData will be invoked with generated UUID; allow any UUID
        doThrow(exception)
            .when(repository)
            .copyData(any(java.util.UUID.class), eq(schema), eq(input));

        IOException thrown =
            assertThrows(
                IOException.class,
                () -> service.createDataset("test-dataset", schema, input)
            );

        assertSame(exception, thrown);
        verify(repository).saveMetadata(any(Dataset.class));
        verify(repository).createTable(any(java.util.UUID.class), eq(schema));
        verify(repository).copyData(any(java.util.UUID.class), eq(schema), eq(input));
    }

    @Test
    void createDatasetPropagatesSQLException() throws Exception {
        DatasetSchema schema = testSchema();
        InputStream input = new ByteArrayInputStream("name,age\nAlice,25\n".getBytes());
        SQLException exception = new SQLException("Database error");

        doThrow(exception)
            .when(repository)
            .createTable(any(java.util.UUID.class), eq(schema));

        SQLException thrown =
            assertThrows(
                SQLException.class,
                () -> service.createDataset("test-dataset", schema, input)
            );

        assertSame(exception, thrown);
        verify(repository).saveMetadata(any(Dataset.class));
        verify(repository).createTable(any(java.util.UUID.class), eq(schema));
        verify(repository, never()).copyData(any(), any(), any());
    }

    /*
     * =========================
     * Find tests
     * =========================
     */

    @Test
    void findDatasetReturnsDatasetWhenRepositoryFindsIt() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        Optional<Dataset> result = service.findDataset(id);

        assertTrue(result.isPresent());
        assertSame(dataset, result.get());
        verify(repository).findById(id);
    }

    @Test
    void findDatasetReturnsEmptyWhenRepositoryDoesNotFindIt() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id)).thenReturn(Optional.empty());

        Optional<Dataset> result = service.findDataset(id);

        assertTrue(result.isEmpty());
        verify(repository).findById(id);
    }

    /*
     * =========================
     * Delete tests
     * =========================
     */

    @Test
    void deleteDatasetDelegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.deleteDataset(id);

        verify(repository).deleteById(id);
    }

    @Test
    void deleteDatasetDoesNotCallOtherRepositoryMethods() throws Exception {
        UUID id = UUID.randomUUID();

        service.deleteDataset(id);

        verify(repository).deleteById(id);
        verify(repository, never()).saveMetadata(any());
        verify(repository, never()).createTable(any(), any());
        verify(repository, never()).copyData(any(), any(), any());
        verify(repository, never()).findById(any());
        verify(repository, never()).query(any(), any());
    }

    /*
     * =========================
     * Query tests
     * =========================
     */

    @Test
    void queryDatasetReturnsResultsFromRepository() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        List<DataRow> expectedResults = List.of(
            new DataRow(java.util.Map.of("name", "Alice", "age", 25)),
            new DataRow(java.util.Map.of("name", "Bob", "age", 30))
        );

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(expectedResults);

        List<DataRow> results = service.queryDataset(id, List.of());

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

        assertEquals("Dataset not found: " + id, exception.getMessage());
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetAllowsNoFilters() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());
        List<DataRow> expectedResults = List.of();

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(id, List.of())).thenReturn(expectedResults);

        List<DataRow> results = service.queryDataset(id, List.of());

        assertTrue(results.isEmpty());
        verify(repository).findById(id);
        verify(repository).query(id, List.of());
    }

    @Test
    void queryDatasetConvertsIntegerFilterValue() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        Filter filter = new Filter("age", FilterOperator.GREATER_THAN, "25");

        service.queryDataset(id, List.of(filter));

        verify(repository).query(
            id,
            List.of(new Filter("age", FilterOperator.GREATER_THAN, 25))
        );
    }

    @Test
    void queryDatasetConvertsMultipleFilterValues() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));
        when(repository.query(eq(id), anyList())).thenReturn(List.of());

        List<Filter> filters = List.of(
            new Filter("name", FilterOperator.EQUALS, "Alice"),
            new Filter("age", FilterOperator.GREATER_THAN, "25")
        );

        service.queryDataset(id, filters);

        verify(repository).query(
            id,
            List.of(
                new Filter("name", FilterOperator.EQUALS, "Alice"),
                new Filter("age", FilterOperator.GREATER_THAN, 25)
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
            List.of(new Filter("id", FilterOperator.EQUALS, "123456789"))
        );

        verify(repository).query(
            id,
            List.of(new Filter("id", FilterOperator.EQUALS, 123456789L))
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
            List.of(new Filter("score", FilterOperator.GREATER_THAN, "95.5"))
        );

        verify(repository).query(
            id,
            List.of(new Filter("score", FilterOperator.GREATER_THAN, 95.5))
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
            List.of(new Filter("active", FilterOperator.EQUALS, "true"))
        );

        verify(repository).query(
            id,
            List.of(new Filter("active", FilterOperator.EQUALS, true))
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
            List.of(new Filter("birthday", FilterOperator.GREATER_THAN, "2020-01-01"))
        );

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
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(new Filter(
                        "does_not_exist",
                        FilterOperator.EQUALS,
                        "Alice"
                    ))
                )
            );

        assertEquals("Unknown column: does_not_exist", exception.getMessage());
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsNullOperator() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(new Filter("age", null, "25"))
                )
            );

        assertEquals("Filter operator must not be null", exception.getMessage());
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsNullFilterValue() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(new Filter("age", FilterOperator.EQUALS, null))
                )
            );

        assertEquals("Filter value must not be null", exception.getMessage());
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetRejectsInvalidIntegerValue() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        IllegalArgumentException exception =
            assertThrows(
                IllegalArgumentException.class,
                () -> service.queryDataset(
                    id,
                    List.of(new Filter("age", FilterOperator.EQUALS, "not-an-integer")))
            );

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
                List.of(new Filter("score", FilterOperator.EQUALS, "not-a-double"))
            )
        );

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
                    List.of(new Filter("active", FilterOperator.EQUALS, "yes"))
                )
            );

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
                List.of(new Filter("birthday", FilterOperator.EQUALS, "not-a-date"))
            )
        );

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
                    List.of(new Filter(
                        "active",
                        FilterOperator.GREATER_THAN,
                        "true"
                    ))
                )
            );

        assertTrue(exception.getMessage().contains("not supported for BOOLEAN"));
        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    @Test
    void queryDatasetDoesNotCallRepositoryQueryWhenValidationFails() {
        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test-dataset", testSchema());

        when(repository.findById(id)).thenReturn(Optional.of(dataset));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.queryDataset(
                id,
                List.of(new Filter(
                    "does_not_exist",
                    FilterOperator.EQUALS,
                    "Alice"
                ))
            )
        );

        verify(repository).findById(id);
        verify(repository, never()).query(any(), any());
    }

    /*
     * =========================
     * Helpers
     * =========================
     */

    private DatasetSchema testSchema() {
        return new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("age", DataType.INTEGER)
        ));
    }
}