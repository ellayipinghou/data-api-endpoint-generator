package com.example.dataserv.storage.postgres;

import com.example.dataserv.domain.*;
import com.example.dataserv.storage.DatasetRepository;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.dataserv.storage.DatasetLoadException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.StringJoiner;

@Repository
public class PostgresDatasetRepository implements DatasetRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostgresDatasetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Saves the dataset's metadata in the dataset table.
     */
    @Override
    public void saveMetadata(Dataset dataset) {
        String sql = 
            """
            INSERT INTO datasets (id, name, created_at)
            VALUES (?, ?, ?)
            """;

        try {
            jdbcTemplate.update(
                sql,
                dataset.getId(),
                dataset.getName(),
                OffsetDateTime.ofInstant(dataset.getCreatedAt(), java.time.ZoneOffset.UTC)
            );
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Failed to save metadata for dataset " + dataset.getId(),
                e
            );
        }
    }

    /**
     * Creates the physical PostgreSQL table for a dataset.
     */
    @Override
    public void createTable(UUID datasetId, DatasetSchema schema) throws SQLException {
        if (schema == null || schema.getColumns().isEmpty()) {
            throw new IllegalArgumentException(
                "Dataset must contain at least one column"
            );
        }

        String tableName = getTableName(datasetId);
        String sql = "CREATE TABLE " + quoteIdentifier(tableName) + " (" + buildColumnDefinitions(schema) + ")";

        try {
            jdbcTemplate.execute((Connection connection) -> {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql.toString());
                }
                return null;
            });
        } catch (RuntimeException e) {
            throw new SQLException("Failed to create table " + tableName, e);
        }
    }

    private String buildColumnDefinitions(DatasetSchema schema) {
    return schema.getColumns()
        .stream()
        .map(column ->
            quoteIdentifier(column.getName())
                + " "
                + column.getType().toPostgresType()
        )
        .collect(Collectors.joining(", "));
}

    /**
     * Bulk-loads data into an already-created dataset table
     * using PostgreSQL COPY.
     *
     * The input is currently expected to be CSV because PostgreSQL
     * COPY is being used with FORMAT csv internally.
     *
     * TODO: handle other types of data
     */
    @Override
    public void copyData(UUID datasetId, DatasetSchema schema, InputStream input) throws SQLException, IOException {
        if (schema == null || schema.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Dataset must contain at least one column");
        }

        if (input == null) {
            throw new IllegalArgumentException("Input stream must not be null");
        }

        String tableName = getTableName(datasetId);

        String sql = 
            "COPY "
            + quoteIdentifier(tableName)
            + " ("
            + buildColumnList(schema)
            + ") FROM STDIN WITH (FORMAT csv, HEADER true)";

        try {
            jdbcTemplate.execute((Connection connection) -> {
                try {
                    PGConnection pgConnection = connection.unwrap(PGConnection.class);
                    CopyManager copyManager = pgConnection.getCopyAPI();
                    copyManager.copyIn(sql, input);
                    return null;
                } catch (SQLException | IOException e) {
                    throw new CopyDataException("Failed to copy data into table " + tableName, e);
                }
            });

        } catch (CopyDataException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PSQLException psqlException) {
                throw new DatasetLoadException(describeServerError(tableName, psqlException), psqlException);
            }
            if (cause instanceof SQLException sqlException) {
                throw sqlException;
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new SQLException("Failed to copy data into table " + tableName, cause);
        } catch (RuntimeException e) {
            throw new SQLException("Failed to copy data into table " + tableName, e);
        }
    }

    private String describeServerError(String tableName, PSQLException e) {
        ServerErrorMessage server = e.getServerErrorMessage();
        if (server == null) {
            return "Failed to load data into " + tableName + ": " + e.getMessage();
        }

        String detail = server.getMessage();
        String where = server.getWhere();

        return where != null ? detail + " (" + where + ")" : detail;
    }

    private String buildColumnList(DatasetSchema schema) {
        return schema.getColumns()
            .stream()
            .map(column -> quoteIdentifier(column.getName()))
            .collect(Collectors.joining(", "));
    }

    /**
     * Finds a dataset by its metadata ID.
     *
     * The schema is reconstructed from the physical PostgreSQL
     * table rather than from a separate dataset_columns table.
     */
    @Override
    public Optional<Dataset> findById(UUID id) {

        String sql = 
            """
            SELECT id, name, created_at
            FROM datasets
            WHERE id = ?
            """;

        try {
            return jdbcTemplate.query(
                sql,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    UUID datasetId = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    Instant createdAt = rs.getObject(
                        "created_at",
                        OffsetDateTime.class
                    ).toInstant();
                    DatasetSchema schema = getSchema(datasetId);

                    return Optional.of(
                        new Dataset(
                            datasetId,
                            name,
                            schema,
                            countRows(datasetId),
                            createdAt
                        )
                    );
                },
                id
            );
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to find dataset " + id,
                    e
            );
        }
    }

    /**
     * Deletes a dataset's physical table and metadata.
     */
    @Override
    public void deleteById(UUID id) {
        String tableName = getTableName(id);
        try {
            dropTable(tableName);
            jdbcTemplate.update("DELETE FROM datasets WHERE id = ?", id);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Failed to delete dataset " + id,
                e
            );
        }
    }

    /**
     * Reads the schema of a physical dataset table from PostgreSQL.
     */
    private DatasetSchema getSchema(UUID datasetId) {
        String tableName = getTableName(datasetId);

        String sql = 
            """
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = ?
            ORDER BY ordinal_position
            """;

        try {
            // query takes a sql string, a RowMapper telling Spring how to turn each result row into an object, and object args
            List<DataColumn> columns = jdbcTemplate.query(
                sql,
                // create new DataColumn with each row that comes back
                (rs, rowNum) -> new DataColumn(
                    rs.getString("column_name"), // name
                    fromPostgresType(rs.getString("data_type")) // type
                ),
                tableName
            );

            return new DatasetSchema(columns);

        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to read schema for dataset " + datasetId, e);
        }
    }

    // given a dataset ID, find the corresponding database table and count how many rows it contains
    private long countRows(UUID datasetId) {
        Long rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + quoteIdentifier(getTableName(datasetId)), // safely quote - table names are SQL identifiers, not normal parameter values
            Long.class
        );
        return rowCount == null ? 0 : rowCount;
    }

    /**
     * Converts a PostgreSQL information_schema data type
     * back into our application's DataType.
     */
    private DataType fromPostgresType(String postgresType) {
        return switch (postgresType) {
            case "character varying", "text" ->
                    DataType.STRING;

            case "integer" ->
                    DataType.INTEGER;

            case "bigint" ->
                    DataType.LONG;

            case "double precision", "real" ->
                    DataType.DOUBLE;

            case "boolean" ->
                    DataType.BOOLEAN;

            case "date" ->
                    DataType.DATE;

            case "timestamp without time zone",
                 "timestamp with time zone" ->
                    DataType.DATETIME;

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported PostgreSQL type: "
                                    + postgresType
                    );
        };
    }

    /**
     * Generates the physical table name for a dataset.
     *
     * Example:
     * dataset_550e8400_e29b_41d4_a716_446655440000
     */
    public String getTableName(UUID datasetId) {
        return "dataset_" + datasetId.toString().replace("-", "_");
    }

    /**
     * Drops a dataset's physical table if it exists.
     */
    private void dropTable(String tableName) {
        String sql = "DROP TABLE IF EXISTS " + quoteIdentifier(tableName);
        jdbcTemplate.execute(sql);
    }

    /**
     * Quotes a PostgreSQL identifier safely.
     *
     * Double quotes inside the identifier are escaped
     * according to PostgreSQL's identifier rules.
     */
    private String quoteIdentifier(String identifier) {
        return "\""
                + identifier.replace("\"", "\"\"")
                + "\"";
    }

    @Override
    public List<DataRow> query(UUID id, List<Filter> filters, List<com.example.dataserv.domain.SortSpec> sort, int limit, int offset) {
        String tableName = getTableName(id);
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT * FROM ");
        sql.append(quoteIdentifier(tableName));

        List<Object> parameters = new ArrayList<>();

        if (!filters.isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }

                Filter filter = filters.get(i);

                sql.append(quoteIdentifier(filter.column()));
                sql.append(" ");

                if (filter.operator() == FilterOperator.LIKE) {
                        // use ILIKE and wrap value for contains semantics; caller should provide wildcard or raw value
                        sql.append("ILIKE ?");
                        parameters.add("%" + filter.value() + "%");
                } else {
                        sql.append(filter.operator().getSqlOperator());
                        sql.append(" ?");
                        parameters.add(filter.value());
                }
            }
        }

        // ORDER BY
        if (sort != null && !sort.isEmpty()) {
            sql.append(" ORDER BY ");
            StringJoiner sj = new StringJoiner(", ");
            for (com.example.dataserv.domain.SortSpec s : sort) {
                    String dir = s.dir() == null ? "asc" : s.dir();
                    dir = dir.equalsIgnoreCase("desc") ? "DESC" : "ASC";
                    sj.add(quoteIdentifier(s.column()) + " " + dir);
            }
            sql.append(sj.toString());
        }

        // LIMIT/OFFSET
        if (limit <= 0) {
            limit = 100;
        }

        sql.append(" LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(Math.max(0, offset));

        return jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> {
                LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    values.put(columnName, rs.getObject(i));
                }
                return new DataRow(values);
            },
            parameters.toArray()
        );
    }

    @Override
    public List<Dataset> findAll() {
        String sql = """
            SELECT
                d.id,
                d.name,
                d.created_at,
                c.column_name,
                c.data_type
            FROM datasets d
            LEFT JOIN information_schema.columns c
                ON c.table_schema = 'public'
                AND c.table_name =
                    'dataset_' ||
                    REPLACE(d.id::text, '-', '_')
            ORDER BY
                d.name,
                c.ordinal_position
            """;

        try {
            return jdbcTemplate.query(
                sql,
                rs -> {
                    Map<UUID, DatasetBuilder> datasets = new LinkedHashMap<>();
                    while (rs.next()) {
                        UUID id = rs.getObject("id", UUID.class);
                        String name = rs.getString("name");

                        Instant createdAt = rs.getObject(
                            "created_at",
                            OffsetDateTime.class
                        ).toInstant();

                        DatasetBuilder builder =
                            datasets.computeIfAbsent(
                                id,
                                key -> new DatasetBuilder(
                                    id,
                                    name,
                                    createdAt
                                )
                        );

                        String columnName = rs.getString("column_name");
                        if (columnName != null) {
                            builder.columns.add(
                                new DataColumn(
                                    columnName,
                                    fromPostgresType(
                                        rs.getString("data_type")
                                        )
                                    )
                                );
                            }
                        }

                        return datasets.values()
                            .stream()
                            .map(DatasetBuilder::build)
                            .toList();
                    }
            );

        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Failed to find datasets",
                e
            );
        }
    }

    private class DatasetBuilder {

        private final UUID id;
        private final String name;
        private final Instant createdAt;
        private final List<DataColumn> columns =
                new ArrayList<>();

        private DatasetBuilder(
                UUID id,
                String name,
                Instant createdAt
        ) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
        }

        private Dataset build() {
            return new Dataset(
                id,
                name,
                new DatasetSchema(columns),
                countRows(id),
                createdAt
            );
        }
    }

    /**
     * Runtime wrapper used to move checked COPY exceptions
     * through JdbcTemplate's callback.
     */
    private static class CopyDataException extends RuntimeException {

        public CopyDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
