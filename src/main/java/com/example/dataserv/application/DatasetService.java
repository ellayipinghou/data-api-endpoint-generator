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
import com.example.dataserv.ingestion.csv.CsvDatasetParser;
import com.example.dataserv.ingestion.csv.CsvParseResult;

import org.apache.commons.csv.CSVRecord;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatasetService {

    private final DatasetRepository repository;

    public DatasetService(DatasetRepository repository) {
        this.repository = repository;
    }

    public DatasetPreviewResponse previewDataset(MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        CsvParseResult parsed = new CsvDatasetParser().parse(new ByteArrayInputStream(content));
        DatasetSchema schema = parsed.schema();

        PreviewStorage storage = new PreviewStorage();
        UUID previewId = storage.save(file);

        List<DataRow> sampleRows = new ArrayList<>();
        List<String> headers = parsed.headers();

        for (CSVRecord record : parsed.previewRows()) {
            Map<String, Object> values = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                values.put(headers.get(i), CsvDatasetParser.cellValue(record, i));
            }
            sampleRows.add(new DataRow(values));
        }

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);
        boolean canSubmit = SchemaValidationHelper.checkCanSubmit(issues);

        return new DatasetPreviewResponse(
                previewId, schema, sampleRows, issues, canSubmit, parsed.typeOptions()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Dataset createDatasetFromPreview(
        String name, UUID previewId, Map<String, DataType> typeOverrides
    ) throws IOException, SQLException {
        PreviewStorage previewStorage = new PreviewStorage();

        if (!previewStorage.exists(previewId)) {
            throw new IllegalArgumentException("Preview not found: " + previewId);
        }
        if (previewStorage.isExpired(previewId, PreviewStorage.DEFAULT_TTL)) {
            previewStorage.delete(previewId);
            throw new IllegalArgumentException("Preview expired: " + previewId);
        }

        byte[] previewBytes;
        try (InputStream input = previewStorage.open(previewId)) {
            previewBytes = input.readAllBytes();
        }

        // full parse (not just parseSchema) so we get typeOptions to validate overrides against
        CsvParseResult parsed = new CsvDatasetParser().parse(new ByteArrayInputStream(previewBytes));
        DatasetSchema schema = applyTypeOverrides(parsed, typeOverrides);

        Dataset dataset;
        try (InputStream input = new ByteArrayInputStream(previewBytes)) {
            dataset = createDataset(name, schema, input);
        }

        previewStorage.delete(previewId);
        return dataset;
    }

    private DatasetSchema applyTypeOverrides(CsvParseResult parsed, Map<String, DataType> typeOverrides) {
        if (typeOverrides == null || typeOverrides.isEmpty()) {
            return parsed.schema();
        }

        List<DataColumn> result = new ArrayList<>();
        for (DataColumn column : parsed.schema().getColumns()) {
            DataType override = typeOverrides.get(column.getName());
            if (override == null) {
                result.add(column);
                continue;
            }

            List<DataType> allowed = parsed.typeOptions().getOrDefault(column.getName(), List.of());
            if (!allowed.contains(override)) {
                throw new InvalidTypeOverrideException(
                    "Column '" + column.getName() + "' cannot be retyped to " + override
                        + "; sampled values only support " + allowed
                );
            }
            result.add(new DataColumn(column.getName(), override));
        }
        return new DatasetSchema(result);
    }

    @Transactional(rollbackFor = Exception.class)
    public Dataset createDataset(String name, DatasetSchema schema, InputStream input) throws IOException, SQLException {
        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);
        boolean canSubmit = SchemaValidationHelper.checkCanSubmit(issues);

        if (!canSubmit) {
            throw new DatasetValidationException("validation failed during createDataset", issues);
        }

        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, name, schema, 0, Instant.now());

        repository.saveMetadata(dataset);
        repository.createTable(id, schema);
        repository.copyData(id, schema, input);

        return dataset;
    }

    public Optional<Dataset> findDataset(UUID id) {
        return repository.findById(id);
    }

    public List<Dataset> findAllDatasets() {
        return repository.findAll();
    }

    @Transactional
    public void deleteDataset(UUID id) {
        repository.deleteById(id);
    }

    public List<DataRow> queryDataset(UUID id, List<Filter> filters) {
        Dataset dataset = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + id));

        List<Filter> validatedFilters = validateAndConvertFilters(dataset.getSchema(), filters);

        return repository.query(id, validatedFilters);
    }

    public List<DataRow> queryDatasetWithParams(UUID id, Map<String, String> params) {
        Dataset dataset = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + id));

        List<Filter> rawFilters = new ArrayList<>();
        List<com.example.dataserv.domain.SortSpec> sort = new ArrayList<>();
        int limit = 100;
        int offset = 0;

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

    private DataColumn findColumn(DatasetSchema schema, String columnName) {
        return schema.getColumns().stream()
                .filter(column -> column.getName().equals(columnName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown column: " + columnName));
    }

    private void validateOperator(DataType type, FilterOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Filter operator must not be null");
        }

        boolean orderedComparison =
                operator == FilterOperator.GREATER_THAN
                        || operator == FilterOperator.GREATER_THAN_OR_EQUAL
                        || operator == FilterOperator.LESS_THAN
                        || operator == FilterOperator.LESS_THAN_OR_EQUAL;

        if (orderedComparison && type == DataType.BOOLEAN) {
            throw new IllegalArgumentException("Operator " + operator + " is not supported for BOOLEAN columns");
        }
    }

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