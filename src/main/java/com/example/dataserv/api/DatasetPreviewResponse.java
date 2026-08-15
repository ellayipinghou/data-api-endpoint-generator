package com.example.dataserv.api;

import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.DatasetSchema;

import java.util.List;
import java.util.UUID;

public class DatasetPreviewResponse {
    private final UUID previewId;
    private final DatasetSchema schema;
    private final List<DataRow> sampleRows;
    private final List<PreviewIssue> issues;
    private final boolean canSubmit;

    public DatasetPreviewResponse(
            UUID previewId,
            DatasetSchema schema,
            List<DataRow> sampleRows,
            List<PreviewIssue> issues,
            boolean canSubmit
    ) {
        this.previewId = previewId;
        this.schema = schema;
        this.sampleRows = sampleRows;
        this.issues = issues;
        this.canSubmit = canSubmit;
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
}
