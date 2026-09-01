package com.example.dataserv.application;

import com.example.dataserv.api.DatasetPreviewResponse;
import com.example.dataserv.api.DatasetValidationException;
import com.example.dataserv.api.InvalidTypeOverrideException;
import com.example.dataserv.api.PreviewIssue;
import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.Dataset;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.domain.Filter;
import com.example.dataserv.domain.FilterOperator;
import com.example.dataserv.storage.DatasetRepository;
import com.example.dataserv.ingestion.DatasetParser;
import com.example.dataserv.ingestion.ParseResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// handles dataset lifecycle: parsing, previewing, creating, querying, and deleting datasets
@Service
public class DatasetService {
    private final DatasetRepository repository;
    private final DatasetParser parser;
    private final PreviewStorage previewStorage; // inject instead of creating

    // inject repository and parser dependencies
    public DatasetService(DatasetRepository repository, DatasetParser parser, PreviewStorage storage) {
        this.repository = repository;
        this.parser = parser;
        this.previewStorage = storage;
    }

    // parse file, validate schema, store preview, and return schema info with any issues
    public DatasetPreviewResponse previewDataset(MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        ParseResult parsed = parser.parse(new ByteArrayInputStream(content));
        DatasetSchema schema = parsed.schema();

        // temporarily store file and its parsed metadata for later dataset creation
        PreviewMetadata metadata = new PreviewMetadata(parsed.schema(), parsed.typeOptions());
        UUID previewId = previewStorage.save(file, metadata);

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);
        boolean canSubmit = SchemaValidationHelper.checkCanSubmit(issues);

        return new DatasetPreviewResponse(
            previewId, schema, parsed.previewRows(), issues, canSubmit, parsed.typeOptions()
        );
    }

    // validate preview exists and hasn't expired, apply type overrides, create dataset, cleanup
    @Transactional(rollbackFor = Exception.class)
    public Dataset createDatasetFromPreview(
        String name, UUID previewId, Map<String, DataType> typeOverrides
    ) throws IOException, SQLException {
        // verify preview exists and is still valid
        if (!previewStorage.exists(previewId)) {
            throw new PreviewMissingException();
        }

        if (previewStorage.isExpired(previewId, PreviewStorage.DEFAULT_TTL)) {
            previewStorage.delete(previewId);
            throw new PreviewMissingException();
        }

        byte[] previewBytes;
        try (InputStream input = previewStorage.open(previewId)) {
            previewBytes = input.readAllBytes();
        }

        // full parse to get typeOptions for validating overrides
        // ParseResult parsed = parser.parse(new ByteArrayInputStream(previewBytes));
        // DatasetSchema schema = applyTypeOverrides(parsed, typeOverrides);
        PreviewMetadata metadata = previewStorage.openMetadata(previewId);
        DatasetSchema schema = applyTypeOverrides(metadata.schema(), typeOverrides, metadata.typeOptions());

        Dataset dataset;
        try (InputStream input = new ByteArrayInputStream(previewBytes)) {
            dataset = createDataset(name, schema, input);
        }

        // cleanup preview storage
        previewStorage.delete(previewId);
        return dataset;
    }

    // apply user-provided type overrides to schema, validating against inferred type options
    private DatasetSchema applyTypeOverrides(DatasetSchema schema, Map<String, DataType> typeOverrides, Map<String, List<DataType>> typeOptions) {
        if (typeOverrides == null || typeOverrides.isEmpty()) {
            return schema;
        }

        for (DataColumn column : schema.getColumns()) {
            DataType override = typeOverrides.get(column.getName());
            if (override == null) {
                continue;
            }

            // ensure override is among the inferred type options
            List<DataType> allowed = typeOptions.getOrDefault(column.getName(), List.of());
            if (!allowed.contains(override)) {
                throw new InvalidTypeOverrideException(
                    "Column '" + column.getName() + "' cannot be retyped to " + override
                        + "; sampled values only support " + allowed
                );
            }
            column.setType(override);
        }
        return schema;
    }

    // validate schema, generate dataset id, save metadata, create table, and load data
    @Transactional(rollbackFor = Exception.class)
    public Dataset createDataset(String name, DatasetSchema schema, InputStream input) throws IOException, SQLException {
        // validate schema before proceeding
        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);
        boolean canSubmit = SchemaValidationHelper.checkCanSubmit(issues);

        if (!canSubmit) {
            throw new DatasetValidationException("validation failed during createDataset", issues);
        }

        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, name, schema, 0, Instant.now());

        // persist metadata, create database table, and load data
        repository.saveMetadata(dataset);
        repository.createTable(id, schema);
        repository.copyData(id, schema, input);

        return dataset;
    }

    // lookup operations for datasets
    public Optional<Dataset> findDataset(UUID id) {
        return repository.findById(id);
    }

    public List<Dataset> findAllDatasets() {
        return repository.findAll();
    }

    // delete dataset and associated data
    @Transactional
    public void deleteDataset(UUID id) {
        repository.deleteById(id);
    }

    // query dataset with filter list, validating filters against schema
    public List<DataRow> queryDataset(UUID id, List<Filter> filters) {
        Dataset dataset = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + id));

        List<Filter> validatedFilters = validateAndConvertFilters(dataset.getSchema(), filters);

        return repository.query(id, validatedFilters);
    }

    // parse query parameters to extract filters, sort, limit, and offset; execute query
    public List<DataRow> queryDatasetWithParams(UUID id, Map<String, String> params) {
        Dataset dataset = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + id));

        List<Filter> rawFilters = new ArrayList<>();
        List<com.example.dataserv.domain.SortSpec> sort = new ArrayList<>();
        int limit = 100;
        int offset = 0;

        // parse query parameters
        for (var entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.equalsIgnoreCase("limit")) {
                try {
                    limit = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
                continue;
            }

            if (key.equalsIgnoreCase("offset")) {
                try {
                    offset = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
                continue;
            }

            if (key.equalsIgnoreCase("sort")) {
                String[] parts = value.split(",");
                if (parts.length >= 1) {
                    String col = parts[0];
                    String dir = parts.length > 1 ? parts[1] : "asc";
                    sort.add(new com.example.dataserv.domain.SortSpec(col, dir));
                }
                continue;
            }

            // map suffixes to filter operators
            String column;
            FilterOperator op;

            if (key.endsWith("_gt")) {
                column = key.substring(0, key.length() - 3);
                op = FilterOperator.GREATER_THAN;
            } else if (key.endsWith("_gte")) {
                column = key.substring(0, key.length() - 4);
                op = FilterOperator.GREATER_THAN_OR_EQUAL;
            } else if (key.endsWith("_lt")) {
                column = key.substring(0, key.length() - 3);
                op = FilterOperator.LESS_THAN;
            } else if (key.endsWith("_lte")) {
                column = key.substring(0, key.length() - 4);
                op = FilterOperator.LESS_THAN_OR_EQUAL;
            } else if (key.endsWith("_ne")) {
                column = key.substring(0, key.length() - 3);
                op = FilterOperator.NOT_EQUALS;
            } else if (key.endsWith("_contains") || key.endsWith("_like")) {
                column = key.endsWith("_contains")
                        ? key.substring(0, key.length() - 9)
                        : key.substring(0, key.length() - 5);
                op = FilterOperator.LIKE;
            } else if (key.endsWith("_eq")) {
                column = key.substring(0, key.length() - 3);
                op = FilterOperator.EQUALS;
            } else {
                column = key;
                op = FilterOperator.EQUALS;
            }

            rawFilters.add(new Filter(column, op, value));
        }

        List<Filter> validatedFilters = validateAndConvertFilters(dataset.getSchema(), rawFilters);

        return repository.query(id, validatedFilters, sort, limit, offset);
    }

    // validate filters against schema and convert values to proper types
    private List<Filter> validateAndConvertFilters(DatasetSchema schema, List<Filter> filters) {
        List<Filter> validatedFilters = new ArrayList<>();

        for (Filter filter : filters) {
            DataColumn column = findColumn(schema, filter.column());
            validateOperator(column.getType(), filter.operator());
            Object convertedValue = convertFilterValue(filter.value(), column.getType());

            validatedFilters.add(new Filter(filter.column(), filter.operator(), convertedValue));
        }

        return validatedFilters;
    }

    // find column by name or throw exception
    private DataColumn findColumn(DatasetSchema schema, String columnName) {
        return schema.getColumns().stream()
                .filter(column -> column.getName().equals(columnName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown column: " + columnName));
    }

    // ensure filter operator is valid for the given data type
    private void validateOperator(DataType type, FilterOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Filter operator must not be null");
        }

        // ordered comparisons cannot be applied to boolean columns
        boolean orderedComparison =
                operator == FilterOperator.GREATER_THAN
                        || operator == FilterOperator.GREATER_THAN_OR_EQUAL
                        || operator == FilterOperator.LESS_THAN
                        || operator == FilterOperator.LESS_THAN_OR_EQUAL;

        if (orderedComparison && type == DataType.BOOLEAN) {
            throw new IllegalArgumentException("Operator " + operator + " is not supported for BOOLEAN columns");
        }
    }

    // convert filter value string to the appropriate data type
    private Object convertFilterValue(Object value, DataType type) {
        if (value == null) {
            throw new IllegalArgumentException("Filter value must not be null");
        }

        String stringValue = value.toString();

        try {
            return switch (type) {
                case STRING -> stringValue;
                case INTEGER -> Integer.valueOf(stringValue);
                case LONG -> Long.valueOf(stringValue);
                case DOUBLE -> Double.valueOf(stringValue);
                case BOOLEAN -> parseBoolean(stringValue);
                case DATE -> LocalDate.parse(stringValue);
                case DATETIME -> LocalDateTime.parse(stringValue);
            };
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Value '" + stringValue + "' is not valid for type " + type, e);
        }
    }

    // parse case-insensitive boolean strings
    private Boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }

        if (value.equalsIgnoreCase("false")) {
            return false;
        }

        throw new IllegalArgumentException("Invalid boolean value: " + value);
    }
}