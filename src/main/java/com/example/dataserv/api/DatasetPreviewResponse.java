package com.example.dataserv.api;

import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DatasetPreviewResponse {
    private final UUID previewId;
    private final DatasetSchema schema;
    private final List<DataRow> sampleRows;
    private final List<PreviewIssue> issues;
    Map<String, List<DataType>> columnTypeOptions;
    private final boolean canSubmit;

    public DatasetPreviewResponse(
            UUID previewId,
            DatasetSchema schema,
            List<DataRow> sampleRows,
            List<PreviewIssue> issues,
            boolean canSubmit,
            Map<String, List<DataType>> columnTypeOptions
    ) {
        this.previewId = previewId;
        this.schema = schema;
        this.sampleRows = sampleRows;
        this.issues = issues;
        this.canSubmit = canSubmit;
        this.columnTypeOptions = columnTypeOptions;
    }

    public UUID getPreviewId() {
        return previewId;
    }

    public DatasetSchema getSchema() {
        return schema;
    }

    public List<DataRow> getSampleRows() {
        return sampleRows;
    }

    public List<PreviewIssue> getIssues() {
        return issues;
    }

    public boolean isCanSubmit() {
        return canSubmit;
    }

    public Map<String, List<DataType>> getColumnTypeOptions() {
        return columnTypeOptions;
    }
}
