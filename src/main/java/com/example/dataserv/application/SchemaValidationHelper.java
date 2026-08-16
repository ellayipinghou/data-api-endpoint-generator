package com.example.dataserv.application;

import com.example.dataserv.api.DatasetValidationException;
import com.example.dataserv.api.PreviewIssue;
import com.example.dataserv.api.PreviewIssueKind;
import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.ingestion.TypeInferer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SchemaValidationHelper {
    private SchemaValidationHelper() {}

    public static List<PreviewIssue> collectIssues(DatasetSchema schema) {
        List<PreviewIssue> issues = new ArrayList<>();
        if (hasHeaderSuspectedDataRow(schema)) {
            issues.add(new PreviewIssue(
                PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW,
                null,
                "The first row may be data, not a header"
            ));
        }

        Set<String> seen = new HashSet<>();

        for (DataColumn column : schema.getColumns()) {
            if (column == null || column.getName() == null || column.getName().isBlank()) {
                issues.add(new PreviewIssue(
                    PreviewIssueKind.EMPTY_COLUMN_NAME,
                    column.getName(),
                    "Column name '" + column.getName() + "' cannot be empty"
                ));
            } else if (!seen.add(column.getName())) {
                issues.add(new PreviewIssue(
                    PreviewIssueKind.DUPLICATE_COLUMN_NAME,
                    column.getName(),
                    "Column name '" + column.getName() + "' is a duplicate, must be unique"
                ));
            } else if (!column.getName().matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
                issues.add(new PreviewIssue(
                    PreviewIssueKind.INVALID_COLUMN_NAME,
                    column.getName(),
                    "Column name '" + column.getName() + "' must start with a letter or underscore and contain only letters, numbers, and underscores"
                ));
            }
        }

        return issues;
    }

    public static boolean checkCanSubmit(List<PreviewIssue> issues) {
        return issues.stream().noneMatch(element -> element.isBlocking());
    }

    private static boolean hasHeaderSuspectedDataRow(DatasetSchema schema) {
        return schema.getColumns().stream().anyMatch(column -> {
            String header = column == null ? null : column.getName();
            if (header == null || header.isBlank()) {
                return true;
            }
            return TypeInferer.inferType(List.of(header)) != DataType.STRING;
        });
    }
}
