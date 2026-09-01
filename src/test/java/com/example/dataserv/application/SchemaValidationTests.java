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
 * exhaustive tests for schema validation logic in isolation:
 * no service, no csv parsing, no mocking - just DatasetSchema in,
 * List<PreviewIssue> / boolean out
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

        // a valid schema should produce no validation issues
        assertTrue(issues.isEmpty());
    }

    @Test
    void collectIssuesReturnsEmptyListForSingleValidColumn() {
        DatasetSchema schema = schemaOf(
            new DataColumn("id", DataType.LONG)
        );

        // a single valid column should be accepted without issues
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

        // an empty column name prevents the dataset from being submitted
        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertFalse(SchemaValidationHelper.checkCanSubmit(issues));
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

        // the first occurrence is valid while each later occurrence is a duplicate
        assertEquals(2, duplicateCount);
    }

    @Test
    void duplicateColumnNameIsBlocking() {
        DatasetSchema schema = schemaOf(
            new DataColumn("name", DataType.STRING),
            new DataColumn("name", DataType.STRING)
        );

        // duplicate names cannot be submitted because the schema is ambiguous
        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertFalse(SchemaValidationHelper.checkCanSubmit(issues));
    }

    /*
     * =========================
     * INVALID_COLUMN_NAME
     * =========================
     */

    @ParameterizedTest
    @ValueSource(strings = {
        "first name", // contains a space
        "age!", // contains punctuation
        "123abc", // starts with a digit
        "na-me" // contains a hyphen
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

        // invalid identifiers must be fixed before the dataset can be submitted
        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertFalse(SchemaValidationHelper.checkCanSubmit(issues));
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

        // these values are valid data values but suspicious as column names
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

        // ordinary identifier-style names should not be mistaken for data values
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

        // this warning is informational, so it should not prevent submission
        assertTrue(SchemaValidationHelper.checkCanSubmit(issues));
    }

    @Test
    void collectIssuesFlagsPurelyNumericHeaderAsBothSuspectedDataRowAndInvalidName() {
        // "1" looks like data and also fails the identifier rules
        DatasetSchema schema = schemaOf(
            new DataColumn("1", DataType.STRING),
            new DataColumn("2", DataType.STRING)
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        assertTrue(issues.stream()
            .anyMatch(i -> i.getKind() == PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW));

        assertTrue(issues.stream()
            .anyMatch(i -> i.getKind() == PreviewIssueKind.INVALID_COLUMN_NAME));

        // the invalid name is blocking even though the suspected-data warning is not
        assertFalse(SchemaValidationHelper.checkCanSubmit(issues));
    }

    /*
     * =========================
     * Multiple issues / combinations
     * =========================
     */

    @Test
    void collectIssuesCanReturnMultipleDistinctIssuesForOneSchema() {
        DatasetSchema schema = schemaOf(
            new DataColumn("", DataType.STRING), // empty name
            new DataColumn("name", DataType.STRING),
            new DataColumn("name", DataType.STRING), // duplicate name
            new DataColumn("bad name!", DataType.STRING) // invalid name
        );

        List<PreviewIssue> issues = SchemaValidationHelper.collectIssues(schema);

        // one schema can surface multiple independent validation problems
        assertTrue(issues.stream().anyMatch(i -> i.getKind() == PreviewIssueKind.EMPTY_COLUMN_NAME));
        assertTrue(issues.stream().anyMatch(i -> i.getKind() == PreviewIssueKind.DUPLICATE_COLUMN_NAME));
        assertTrue(issues.stream().anyMatch(i -> i.getKind() == PreviewIssueKind.INVALID_COLUMN_NAME));
    }

    /*
     * =========================
     * checkCanSubmit
     * =========================
     */

    @Test
    void checkCanSubmitReturnsTrueForEmptyIssueList() {
        // no issues means there is nothing preventing submission
        assertTrue(SchemaValidationHelper.checkCanSubmit(List.of()));
    }

    @Test
    void checkCanSubmitReturnsTrueWhenOnlyNonBlockingIssuesPresent() {
        List<PreviewIssue> issues = List.of(
            new PreviewIssue(
                PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW,
                "random_col",
                "looks like data"
            )
        );

        // non-blocking warnings should still allow the dataset to be submitted
        assertTrue(SchemaValidationHelper.checkCanSubmit(issues));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "EMPTY_COLUMN_NAME",
        "DUPLICATE_COLUMN_NAME",
        "INVALID_COLUMN_NAME"
    })
    void checkCanSubmitReturnsFalseWhenAnyBlockingIssuePresent(String kindName) {
        PreviewIssueKind kind = PreviewIssueKind.valueOf(kindName);
        List<PreviewIssue> issues = List.of(
            new PreviewIssue(kind, "random_col", "some message")
        );

        // any blocking issue is enough to prevent submission
        assertFalse(SchemaValidationHelper.checkCanSubmit(issues));
    }

    @Test
    void checkCanSubmitReturnsFalseWhenBlockingIssueMixedWithNonBlocking() {
        List<PreviewIssue> issues = List.of(
            new PreviewIssue(
                PreviewIssueKind.HEADER_SUSPECTED_DATA_ROW,
                "random_col",
                "looks like data"
            ),
            new PreviewIssue(
                PreviewIssueKind.EMPTY_COLUMN_NAME,
                null,
                "blank"
            )
        );

        // a blocking issue still prevents submission when warnings are also present
        assertFalse(SchemaValidationHelper.checkCanSubmit(issues));
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
