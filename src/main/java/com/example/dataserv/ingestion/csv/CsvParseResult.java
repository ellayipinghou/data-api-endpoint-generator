package com.example.dataserv.ingestion.csv;

import com.example.dataserv.domain.DatasetSchema;
import org.apache.commons.csv.CSVRecord;

import java.util.List;

/**
 * Result of a single CSV parse pass: the inferred schema, the raw header
 * names in column order, and up to CsvDatasetParser.PREVIEW_SAMPLE_SIZE
 * parsed rows for preview use.
 */
public record CsvParseResult(
        DatasetSchema schema,
        List<String> headers,
        List<CSVRecord> previewRows
) {}