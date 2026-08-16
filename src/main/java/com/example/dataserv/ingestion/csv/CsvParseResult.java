package com.example.dataserv.ingestion.csv;

import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;
import org.apache.commons.csv.CSVRecord;

import java.util.List;
import java.util.Map;

/**
 * Result of a single CSV parse pass: the inferred schema, the raw header
 * names in column order, up to CsvDatasetParser.PREVIEW_SAMPLE_SIZE parsed
 * rows for preview use, and - per column - the full set of types that
 * column's sampled values would be compatible with, for populating a
 * retype dropdown in the preview UI.
 */
public record CsvParseResult(
        DatasetSchema schema,
        List<String> headers,
        List<CSVRecord> previewRows,
        Map<String, List<DataType>> typeOptions
) {}