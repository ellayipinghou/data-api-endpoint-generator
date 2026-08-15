package com.example.dataserv.application;

import com.example.dataserv.api.DatasetValidationException;
import com.example.dataserv.api.PreviewIssue;
import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DatasetSchema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SchemaValidationHelper {

    private SchemaValidationHelper() {}

    public static List<PreviewIssue> validatePreview(DatasetSchema schema) {
        List<PreviewIssue> issues = new ArrayList<>();
        issues.addAll(validateSchema(schema));

        if (hasHeaderSuspectedDataRow(schema)) {
            issues.add(new PreviewIssue(
                "HEADER_SUSPECTED_DATA_ROW",
                null,
                "The first row looks like data, not a header.",
                false
            ));
        }

        return issues;
    }

    public static List<PreviewIssue> validateCreate(DatasetSchema schema) {
        return validateSchema(schema);
    }

    public static boolean canSubmit(List<PreviewIssue> issues) {
        return issues.stream().noneMatch(element -> element.getIsBlocking());
    }

    public static void assertCreateAllowed(DatasetSchema schema) {
        List<PreviewIssue> issues = validateCreate(schema);
        if (!issues.isEmpty()) {
            throw new DatasetValidationException("Dataset validation failed", issues);
        }
    }

    private static List<PreviewIssue> validateSchema(DatasetSchema schema) {
        List<PreviewIssue> issues = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (DataColumn column : schema.getColumns()) {
            if (column == null || column.getName() == null || column.getName().isBlank()) {
                issues.add(new PreviewIssue(
                        "EMPTY_NAME",
                        column == null ? null : column.getName(),
                        "Column name is empty",
                        true
                ));
            } else if (!seen.add(column.getName())) {
                issues.add(new PreviewIssue(
                        "DUPLICATE_NAME",
                        column.getName(),
                        "Duplicate column name",
                        true
                ));
            }
        }

        return issues;
    }

    private static boolean hasHeaderSuspectedDataRow(DatasetSchema schema) {
        return schema.getColumns().stream().anyMatch(column -> {
            String header = column.getName();
            if (header == null) {
                return true;
            }
            header = header.trim();
            if (header.isEmpty()) {
                return true;
            }

            try {
                Integer.parseInt(header);
                return true;
            } catch (NumberFormatException ignored) {
                // continue
            }

            try {
                Long.parseLong(header);
                return true;
            } catch (NumberFormatException ignored) {
                // continue
            }

            try {
                Double.parseDouble(header);
                return true;
            } catch (NumberFormatException ignored) {
                // continue
            }

            try {
                LocalDate.parse(header);
                return true;
            } catch (RuntimeException ignored) {
                // continue
            }

            try {
                LocalDateTime.parse(header);
                return true;
            } catch (RuntimeException ignored) {
                // continue
            }

            return false;
        });
    }
}
