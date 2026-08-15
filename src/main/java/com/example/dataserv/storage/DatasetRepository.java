package com.example.dataserv.storage;

import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.domain.Filter;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatasetRepository {
    List<Dataset> findAll();

    void saveMetadata(Dataset dataset);

    void createTable(UUID id, DatasetSchema schema) throws SQLException;

    void copyData(UUID id, DatasetSchema schema, InputStream input) throws SQLException, IOException; 

    Optional<Dataset> findById(UUID id);

    void deleteById(UUID id);

    List<DataRow> query(
        UUID id,
        List<Filter> filters,
        List<com.example.dataserv.domain.SortSpec> sort,
        int limit,
        int offset
    );

    default List<DataRow> query(UUID id, List<Filter> filters) {
        return query(id, filters, List.of(), 100, 0);
    }
}