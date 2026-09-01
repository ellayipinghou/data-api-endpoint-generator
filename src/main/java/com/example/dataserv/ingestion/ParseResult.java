package com.example.dataserv.ingestion;

import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;

import java.util.List;
import java.util.Map;

/**
 * Result of a single parse pass: the inferred schema, the raw header
 * names in column order, up toPREVIEW_SAMPLE_SIZE parsed
 * rows for preview use, and - per column - the full set of types that
 * column's sampled values would be compatible with, for populating a
 * retype dropdown in the preview UI.
 */
public record ParseResult(
    DatasetSchema schema,
    List<String> headers,
    List<DataRow> previewRows,
    Map<String, List<DataType>> typeOptions
) {}