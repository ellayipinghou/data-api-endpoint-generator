package com.example.dataserv.application;

import com.example.dataserv.api.PreviewIssue;
import com.example.dataserv.api.PreviewIssueKind;
import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive tests for schema validation logic in isolation:
 * no service, no CSV parsing, no mocking - just DatasetSchema in,
 * List<PreviewIssue> / boolean out.
 */
class SchemaValidationHelperTests {

    /*
     * =========================
     * collectIssues - valid schema
     * =========================
     */

    @Test
    void collectIssuesReturnsEmptyListForValidSchema() {
        DatasetSchema schema = schemaOf(
                new DataColumn("name", DataType.STRING),
                new DataColumn("age", DataType.INTEGER)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.isEmpty());
    }

    @Test
    void collectIssuesReturnsEmptyListForSingleValidColumn() {
        DatasetSchema schema = schemaOf(
                new DataColumn("id", DataType.LONG)
        );

        assertTrue(SchemaValidationHelper.collectIssues(schema).isEmpty());
    }

    /*
     * =========================
     * EMPTY_COLUMN_NAME
     * =========================
     */

    @Test
    void collectIssuesFlagsEmptyStringColumnName() {
        DatasetSchema schema = schemaOf(
                new DataColumn("", DataType.STRING),
                new DataColumn("age", DataType.INTEGER)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.EMPTY_COLUMN_NAME));
    }

    @Test
    void collectIssuesFlagsBlankColumnName() {
        DatasetSchema schema = schemaOf(
                new DataColumn("   ", DataType.STRING),
                new DataColumn("age", DataType.INTEGER)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.EMPTY_COLUMN_NAME));
    }

    @Test
    void emptyColumnNameIsBlocking() {
        DatasetSchema schema = schemaOf(
                new DataColumn("", DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertFalse(SchemaValidationHelper.validateSchema(issues));
    }

    /*
     * =========================
     * DUPLICATE_COLUMN_NAME
     * =========================
     */

    @Test
    void collectIssuesFlagsDuplicateColumnNames() {
        DatasetSchema schema = schemaOf(
                new DataColumn("name", DataType.STRING),
                new DataColumn("name", DataType.INTEGER)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.DUPLICATE_COLUMN_NAME));
    }

    @Test
    void collectIssuesOnlyFlagsSecondOccurrenceAsDuplicate() {
        DatasetSchema schema = schemaOf(
                new DataColumn("name", DataType.STRING),
                new DataColumn("name", DataType.STRING),
                new DataColumn("name", DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        long duplicateCount = issues.stream()
                .filter(i -> i.getKind() == PreviewIssueKind.DUPLICATE_COLUMN_NAME)
                .count();

        // first "name" is fine, 2nd and 3rd are duplicates
        assertEquals(2, duplicateCount);
    }

    @Test
    void duplicateColumnNameIsBlocking() {
        DatasetSchema schema = schemaOf(
                new DataColumn("name", DataType.STRING),
                new DataColumn("name", DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertFalse(SchemaValidationHelper.validateSchema(issues));
    }

    /*
     * =========================
     * INVALID_COLUMN_NAME
     * =========================
     */

    @ParameterizedTest
    @ValueSource(strings = {
            "first name",   // contains a space
            "age!",         // punctuation
            "123abc",       // starts with a digit, but not purely numeric
            "na-me"         // hyphen
    })
    void collectIssuesFlagsInvalidColumnNames(String columnName) {
        DatasetSchema schema = schemaOf(
                new DataColumn(columnName, DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.INVALID_COLUMN_NAME));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "name",
            "_name",
            "Name123",
            "first_name",
            "_"
    })
    void collectIssuesAllowsValidIdentifierColumnNames(String columnName) {
        DatasetSchema schema = schemaOf(
                new DataColumn(columnName, DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .noneMatch(i -> i.getKind() == PreviewIssueKind.INVALID_COLUMN_NAME));
    }

    @Test
    void invalidColumnNameIsBlocking() {
        DatasetSchema schema = schemaOf(
                new DataColumn("first name", DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertFalse(SchemaValidationHelper.validateSchema(issues));
    }

    /*
     * =========================
     * HEADER_SUSPECTED_DATA_ROW
     * =========================
     */

    @ParameterizedTest
    @ValueSource(strings = {
            "1",                    // integer
            "123456789012",         // long
            "3.14",                 // double
            "true",                 // boolean
            "false",                // boolean
            "2020-01-01",           // date
            "2020-01-01T12:30:00"   // datetime
    })
    void collectIssuesFlagsHeadersThatLookLikeData(String columnName) {
        DatasetSchema schema = schemaOf(
                new DataColumn(columnName, DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "age", "created_at", "_internal"})
    void collectIssuesDoesNotFlagRealisticHeadersAsSuspectedDataRow(String columnName) {
        DatasetSchema schema = schemaOf(
                new DataColumn(columnName, DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .noneMatch(i -> i.getKind() == PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW));
    }

    @Test
    void headerSuspectedDataRowIsNotBlockingOnItsOwn() {
        DatasetSchema schema = schemaOf(
                new DataColumn("true", DataType.STRING),
                new DataColumn("false", DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW));
        assertTrue(SchemaValidationHelper.validateSchema(issues));
    }

    @Test
    void collectIssuesFlagsPurelyNumericHeaderAsBothSuspectedDataRowAndInvalidName() {
        // "1" both looks like data AND fails the identifier regex (starts with a digit) -
        // both issues are expected to surface, and the blocking one (INVALID_COLUMN_NAME)
        // means this schema cannot be submitted as-is.
        DatasetSchema schema = schemaOf(
                new DataColumn("1", DataType.STRING),
                new DataColumn("2", DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW));
        assertTrue(issues.stream()
                .anyMatch(i -> i.getKind() == PreviewIssueKind.INVALID_COLUMN_NAME));
        assertFalse(SchemaValidationHelper.validateSchema(issues));
    }

    /*
     * =========================
     * Multiple issues / combinations
     * =========================
     */

    @Test
    void collectIssuesCanReturnMultipleDistinctIssuesForOneSchema() {
        DatasetSchema schema = schemaOf(
                new DataColumn("", DataType.STRING),          // empty
                new DataColumn("name", DataType.STRING),
                new DataColumn("name", DataType.STRING),       // duplicate
                new DataColumn("bad name!", DataType.STRING)   // invalid
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream().anyMatch(i -> i.getKind() == PreviewIssueKind.EMPTY_COLUMN_NAME));
        assertTrue(issues.stream().anyMatch(i -> i.getKind() == PreviewIssueKind.DUPLICATE_COLUMN_NAME));
        assertTrue(issues.stream().anyMatch(i -> i.getKind() == PreviewIssueKind.INVALID_COLUMN_NAME));
    }

    /*
     * =========================
     * validateSchema
     * =========================
     */

    @Test
    void validateSchemaReturnsTrueForEmptyIssueList() {
        assertTrue(SchemaValidationHelper.validateSchema(List.of()));
    }

    @Test
    void validateSchemaReturnsTrueWhenOnlyNonBlockingIssuesPresent() {
        List<PreviewIssue> issues = List.of(
                new PreviewIssue(PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW, "looks like data")
        );

        assertTrue(SchemaValidationHelper.validateSchema(issues));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "EMPTY_COLUMN_NAME",
            "DUPLICATE_COLUMN_NAME",
            "INVALID_COLUMN_NAME"
    })
    void validateSchemaReturnsFalseWhenAnyBlockingIssuePresent(String kindName) {
        PreviewIssueKind kind = PreviewIssueKind.valueOf(kindName);
        List<PreviewIssue> issues = List.of(new PreviewIssue(kind, "some message"));

        assertFalse(SchemaValidationHelper.validateSchema(issues));
    }

    @Test
    void validateSchemaReturnsFalseWhenBlockingIssueMixedWithNonBlocking() {
        List<PreviewIssue> issues = List.of(
                new PreviewIssue(PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW, "looks like data"),
                new PreviewIssue(PreviewIssueKind.EMPTY_COLUMN_NAME, "blank")
        );

        assertFalse(SchemaValidationHelper.validateSchema(issues));
    }

    /*
     * =========================
     * Helpers
     * =========================
     */

    private static DatasetSchema schemaOf(DataColumn... columns) {
        return new DatasetSchema(List.of(columns));
    }
}