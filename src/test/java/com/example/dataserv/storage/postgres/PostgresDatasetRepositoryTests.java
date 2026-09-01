package com.example.dataserv.storage.postgres;

import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.domain.Filter;
import com.example.dataserv.domain.FilterOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostgresDatasetRepositoryTests {

    @Autowired
    private PostgresDatasetRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID createdDatasetId;

    @AfterEach
    void cleanup() {
        // remove the dynamic table and metadata created by each test
        if (createdDatasetId != null) {
            repository.deleteById(createdDatasetId);
            createdDatasetId = null;
        }
    }

    @Test
    void savesDatasetMetadata() {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Dataset dataset = testDataset(id);

        repository.saveMetadata(dataset);

        // verify metadata was persisted independently of the dynamic data table
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM datasets WHERE id = ?",
            Integer.class,
            id
        );

        assertEquals(1, count);

        String name = jdbcTemplate.queryForObject(
            "SELECT name FROM datasets WHERE id = ?",
            String.class,
            id
        );

        assertEquals("test-dataset", name);
    }

    @Test
    void createsDynamicTable() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Dataset dataset = testDataset(id);

        repository.createTable(id, dataset.getSchema());

        String tableName = repository.getTableName(id);

        // query postgres metadata to verify the table was actually created
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
                AND table_name = ?
            """,
            Integer.class,
            tableName
        );

        assertEquals(1, count);

        // verify the physical table matches the two-column schema
        Integer columnCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = 'public'
                AND table_name = ?
            """,
            Integer.class,
            tableName
        );

        assertEquals(2, columnCount);
    }

    @Test
    void copyDataInsertsRowsIntoDynamicTable() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Dataset dataset = testDataset(id);

        repository.createTable(id, dataset.getSchema());

        // load csv data into the table created for this dataset
        repository.copyData(
            id,
            dataset.getSchema(),
            input("""
                name,age
                Alice,25
                Bob,30
                Charlie,35
                """)
        );

        String tableName = repository.getTableName(id);

        // verify all input rows were inserted
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + quoteIdentifier(tableName),
            Integer.class
        );

        assertEquals(3, count);

        // order by age so the result order is deterministic
        List<String> names = jdbcTemplate.queryForList(
            """
            SELECT "name"
            FROM %s
            ORDER BY "age"
            """.formatted(quoteIdentifier(tableName)),
            String.class
        );

        assertEquals(List.of("Alice", "Bob", "Charlie"), names);
    }

    @Test
    void copyDataStoresValuesUsingCorrectPostgresTypes() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;

        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("age", DataType.INTEGER),
            new DataColumn("score", DataType.DOUBLE),
            new DataColumn("active", DataType.BOOLEAN),
            new DataColumn("birthday", DataType.DATE)
        ));

        repository.createTable(id, schema);

        // use one row containing each supported typed value
        repository.copyData(
            id,
            schema,
            input("""
                name,age,score,active,birthday
                Alice,25,95.5,true,2020-01-15
                """)
        );

        String tableName = repository.getTableName(id);

        Integer age = jdbcTemplate.queryForObject(
            "SELECT \"age\" FROM " + quoteIdentifier(tableName),
            Integer.class
        );

        Double score = jdbcTemplate.queryForObject(
            "SELECT \"score\" FROM " + quoteIdentifier(tableName),
            Double.class
        );

        Boolean active = jdbcTemplate.queryForObject(
            "SELECT \"active\" FROM " + quoteIdentifier(tableName),
            Boolean.class
        );

        LocalDate birthday = jdbcTemplate.queryForObject(
            "SELECT \"birthday\" FROM " + quoteIdentifier(tableName),
            LocalDate.class
        );

        // verify postgres returns values using the expected java types and values
        assertEquals(25, age);
        assertEquals(95.5, score, 0.000001);
        assertTrue(active);
        assertEquals(LocalDate.of(2020, 1, 15), birthday);
    }

    @Test
    void copyDataFailsWhenValueDoesNotMatchColumnType() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;

        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("age", DataType.INTEGER)
        ));

        repository.createTable(id, schema);

        String invalidCsv = """
            name,age
            Alice,25
            Bob,not-an-integer
            Charlie,35
            """;

        // invalid integer data should make the bulk load fail rather than silently convert it
        assertThrows(
            Exception.class,
            () -> repository.copyData(id, schema, input(invalidCsv))
        );

        String tableName = repository.getTableName(id);

        // the failed data load should not remove the table itself
        Integer tableCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
                AND table_name = ?
            """,
            Integer.class,
            tableName
        );

        assertEquals(1, tableCount);
    }

    @Test
    void findByIdReturnsDatasetAndReadsSchemaFromPhysicalTable() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Dataset dataset = testDataset(id);

        repository.saveMetadata(dataset);
        repository.createTable(id, dataset.getSchema());

        Optional<Dataset> result = repository.findById(id);

        assertTrue(result.isPresent());

        Dataset found = result.get();

        assertEquals(id, found.getId());
        assertEquals("test-dataset", found.getName());

        // verify the schema is reconstructed from the physical postgres table
        assertEquals(2, found.getSchema().getColumns().size());
        assertEquals("name", found.getSchema().getColumns().get(0).getName());
        assertEquals(DataType.STRING, found.getSchema().getColumns().get(0).getType());
        assertEquals("age", found.getSchema().getColumns().get(1).getName());
        assertEquals(DataType.INTEGER, found.getSchema().getColumns().get(1).getType());
    }

    @Test
    void findByIdReturnsPersistedCreatedAtAndLiveRowCount() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Instant createdAt = Instant.parse("2026-08-13T12:00:00Z");

        Dataset dataset = new Dataset(
            id,
            "test-dataset",
            testDataset(id).getSchema(),
            0,
            createdAt
        );

        repository.saveMetadata(dataset);
        repository.createTable(id, dataset.getSchema());

        // insert rows after saving metadata so the returned count must come from the live table
        repository.copyData(
            id,
            dataset.getSchema(),
            input("""
                name,age
                Alice,25
                Bob,30
                Charlie,35
                """)
        );

        Dataset found = repository.findById(id).orElseThrow();

        // createdAt comes from persisted metadata while rowCount reflects current table contents
        assertEquals(createdAt, found.getCreatedAt());
        assertEquals(3, found.getRowCount());
    }

    @Test
    void findByIdReturnsEmptyForUnknownDataset() {
        UUID id = UUID.randomUUID();

        Optional<Dataset> result = repository.findById(id);

        assertTrue(result.isEmpty());
    }

    @Test
    void deleteByIdRemovesMetadataAndDynamicTable() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Dataset dataset = testDataset(id);

        repository.saveMetadata(dataset);
        repository.createTable(id, dataset.getSchema());

        String tableName = repository.getTableName(id);

        assertTrue(repository.findById(id).isPresent());

        // confirm the physical table exists before deletion
        Integer tableCountBeforeDelete = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
                AND table_name = ?
            """,
            Integer.class,
            tableName
        );

        assertEquals(1, tableCountBeforeDelete);

        repository.deleteById(id);

        // deletion should remove both the metadata and dynamic table
        assertTrue(repository.findById(id).isEmpty());

        Integer tableCountAfterDelete = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
                AND table_name = ?
            """,
            Integer.class,
            tableName
        );

        assertEquals(0, tableCountAfterDelete);

        // prevent cleanup from attempting to delete the same dataset again
        createdDatasetId = null;
    }

    /*
     * =========================
     * Query tests
     * =========================
     */

    @Test
    void queryReturnsAllRowsWhenThereAreNoFilters() throws Exception {
        UUID id = createDatasetWithRows();

        // an empty filter list should return every row in the dataset
        List<DataRow> results = repository.query(id, List.of());

        assertEquals(3, results.size());
        assertEquals("Alice", results.get(0).getValues().get("name"));
        assertEquals(25, results.get(0).getValues().get("age"));
        assertEquals("Bob", results.get(1).getValues().get("name"));
        assertEquals(30, results.get(1).getValues().get("age"));
        assertEquals("Charlie", results.get(2).getValues().get("name"));
        assertEquals(35, results.get(2).getValues().get("age"));
    }

    @Test
    void queryFiltersByEquality() throws Exception {
        UUID id = createDatasetWithRows();

        // equality should return only the row whose name matches the filter
        List<DataRow> results = repository.query(
            id,
            List.of(new Filter("name", FilterOperator.EQUALS, "Bob"))
        );

        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).getValues().get("name"));
        assertEquals(30, results.get(0).getValues().get("age"));
    }

    @Test
    void queryFiltersIntegersUsingGreaterThan() throws Exception {
        UUID id = createDatasetWithRows();

        // greater-than should exclude the boundary value itself
        List<DataRow> results = repository.query(
            id,
            List.of(new Filter("age", FilterOperator.GREATER_THAN, 25))
        );

        assertEquals(2, results.size());
        assertEquals("Bob", results.get(0).getValues().get("name"));
        assertEquals("Charlie", results.get(1).getValues().get("name"));
    }

    @Test
    void queryFiltersIntegersUsingLessThan() throws Exception {
        UUID id = createDatasetWithRows();

        // less-than should exclude the boundary value itself
        List<DataRow> results = repository.query(
            id,
            List.of(new Filter("age", FilterOperator.LESS_THAN, 35))
        );

        assertEquals(2, results.size());
        assertEquals("Alice", results.get(0).getValues().get("name"));
        assertEquals("Bob", results.get(1).getValues().get("name"));
    }

    @Test
    void querySupportsMultipleFilters() throws Exception {
        UUID id = createDatasetWithRows();

        // multiple filters should be combined so only rows satisfying both remain
        List<DataRow> results = repository.query(
            id,
            List.of(
                new Filter("age", FilterOperator.GREATER_THAN, 20),
                new Filter("age", FilterOperator.LESS_THAN, 35)
            )
        );

        assertEquals(2, results.size());
        assertEquals("Alice", results.get(0).getValues().get("name"));
        assertEquals("Bob", results.get(1).getValues().get("name"));
    }

    @Test
    void queryReturnsEmptyListWhenNoRowsMatch() throws Exception {
        UUID id = createDatasetWithRows();

        // a filter outside the dataset range should produce no matches
        List<DataRow> results = repository.query(
            id,
            List.of(new Filter("age", FilterOperator.GREATER_THAN, 100))
        );

        assertTrue(results.isEmpty());
    }

    @Test
    void queryFiltersBooleanValues() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;

        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("active", DataType.BOOLEAN)
        ));

        repository.createTable(id, schema);

        // store both boolean values so the equality filter can distinguish them
        repository.copyData(
            id,
            schema,
            input("""
                name,active
                Alice,true
                Bob,false
                Charlie,true
                """)
        );

        List<DataRow> results = repository.query(
            id,
            List.of(new Filter("active", FilterOperator.EQUALS, true))
        );

        assertEquals(2, results.size());
        assertEquals("Alice", results.get(0).getValues().get("name"));
        assertEquals("Charlie", results.get(1).getValues().get("name"));
    }

    @Test
    void queryFiltersDates() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;

        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("birthday", DataType.DATE)
        ));

        repository.createTable(id, schema);

        // use dates on both sides of the boundary to verify typed comparison
        repository.copyData(
            id,
            schema,
            input("""
                name,birthday
                Alice,2000-01-01
                Bob,2010-05-15
                Charlie,2020-10-20
                """)
        );

        List<DataRow> results = repository.query(
            id,
            List.of(
                new Filter(
                    "birthday",
                    FilterOperator.GREATER_THAN,
                    LocalDate.of(2005, 1, 1)
                )
            )
        );

        assertEquals(2, results.size());
        assertEquals("Bob", results.get(0).getValues().get("name"));
        assertEquals("Charlie", results.get(1).getValues().get("name"));
    }

    @Test
    void createTableRejectsEmptySchema() {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        DatasetSchema emptySchema = new DatasetSchema(List.of());

        // an empty schema cannot produce a valid dynamic table
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> repository.createTable(id, emptySchema)
        );
    }

    @Test
    void copyDataRejectsNullInput() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Dataset dataset = testDataset(id);

        repository.createTable(id, dataset.getSchema());

        // null input is invalid because there is no data source to load
        assertThrows(
            InvalidDataAccessApiUsageException.class,
            () -> repository.copyData(id, dataset.getSchema(), null)
        );
    }

    private UUID createDatasetWithRows() throws Exception {
        UUID id = UUID.randomUUID();
        createdDatasetId = id;
        Dataset dataset = testDataset(id);

        repository.createTable(id, dataset.getSchema());

        // keep the shared fixture small so query assertions focus on filter behavior
        repository.copyData(
            id,
            dataset.getSchema(),
            input("""
                name,age
                Alice,25
                Bob,30
                Charlie,35
                """)
        );

        return id;
    }

    private Dataset testDataset(UUID id) {
        // use a simple schema shared by tests that only need basic string and integer data
        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("age", DataType.INTEGER)
        ));

        return new Dataset(id, "test-dataset", schema);
    }

    @Test
    void findAllReturnsAllDatasetsWithTheirSchemas() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Dataset dataset1 = new Dataset(
            id1,
            "dataset-one",
            new DatasetSchema(List.of(
                new DataColumn("name", DataType.STRING),
                new DataColumn("age", DataType.INTEGER)
            ))
        );

        Dataset dataset2 = new Dataset(
            id2,
            "dataset-two",
            new DatasetSchema(List.of(
                new DataColumn("title", DataType.STRING),
                new DataColumn("score", DataType.DOUBLE),
                new DataColumn("active", DataType.BOOLEAN)
            ))
        );

        try {
            // create two distinct datasets so findAll can be checked by id
            repository.saveMetadata(dataset1);
            repository.createTable(id1, dataset1.getSchema());

            repository.saveMetadata(dataset2);
            repository.createTable(id2, dataset2.getSchema());

            List<Dataset> results = repository.findAll();

            assertTrue(results.size() >= 2);

            Dataset found1 = results.stream()
                .filter(dataset -> dataset.getId().equals(id1))
                .findFirst()
                .orElseThrow();

            Dataset found2 = results.stream()
                .filter(dataset -> dataset.getId().equals(id2))
                .findFirst()
                .orElseThrow();

            // verify both metadata and physical schemas were reconstructed correctly
            assertEquals("dataset-one", found1.getName());
            assertEquals(dataset1.getSchema().getColumns(), found1.getSchema().getColumns());
            assertEquals("dataset-two", found2.getName());
            assertEquals(dataset2.getSchema().getColumns(), found2.getSchema().getColumns());
        } finally {
            // clean up both datasets even if one of the assertions fails
            repository.deleteById(id1);
            repository.deleteById(id2);
        }
    }

    private ByteArrayInputStream input(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String quoteIdentifier(String identifier) {
        // quote dynamic table and column names so generated SQL remains valid
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
