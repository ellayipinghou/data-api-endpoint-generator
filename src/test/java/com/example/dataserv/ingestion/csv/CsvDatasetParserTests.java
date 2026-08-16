package com.example.dataserv.ingestion.csv;

import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvDatasetParserTests {

    private final CsvDatasetParser parser = new CsvDatasetParser();

    /*
     * =========================
     * parseSchema - type inference
     * =========================
     */

    @Test
    void infersSchemaFromCsv() throws Exception {
        String csv = """
                name,age,score,active,birthday
                Alice,25,95.5,true,2020-01-15
                Bob,30,87.2,false,1999-05-20
                """;

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(5, schema.getColumns().size());
        assertEquals(DataType.STRING, schema.getColumns().get(0).getType());
        assertEquals(DataType.INTEGER, schema.getColumns().get(1).getType());
        assertEquals(DataType.DOUBLE, schema.getColumns().get(2).getType());
        assertEquals(DataType.BOOLEAN, schema.getColumns().get(3).getType());
        assertEquals(DataType.DATE, schema.getColumns().get(4).getType());
    }

    @Test
    void infersLongTypeForValuesTooLargeForInteger() throws Exception {
        String csv = """
                id
                9999999999
                8888888888
                """;

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(DataType.LONG, schema.getColumns().get(0).getType());
    }

    @Test
    void infersDatetimeType() throws Exception {
        String csv = """
                created
                2025-01-01T12:30:00
                2025-06-15T08:00:00
                """;

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(DataType.DATETIME, schema.getColumns().get(0).getType());
    }

    @Test
    void fallsBackToStringWhenColumnValuesAreInconsistentlyTyped() throws Exception {
        String csv = """
                mixed
                25
                notanumber
                """;

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(DataType.STRING, schema.getColumns().get(0).getType());
    }

    @Test
    void treatsBlankValuesAsCompatibleWithAnyTypeDuringInference() throws Exception {
        String csv = """
                age
                25

                30
                """;

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(DataType.INTEGER, schema.getColumns().get(0).getType());
    }

    @Test
    void trimsWhitespaceFromValuesBeforeTypeInference() throws Exception {
        String csv = "flag\ntrue, false\n true,true\nfalse, true\n";

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(DataType.BOOLEAN, schema.getColumns().get(0).getType());
    }

    @Test
    void trimsWhitespaceFromHeaderNames() throws Exception {
        String csv = " name , age \nAlice,25\n";

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals("name", schema.getColumns().get(0).getName());
        assertEquals("age", schema.getColumns().get(1).getName());
    }

    @Test
    void defaultsToStringWhenColumnHasNoNonEmptyValues() throws Exception {
        String csv = "name,age\nAlice,\nBob,\n";

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(DataType.STRING, schema.getColumns().get(1).getType());
    }

    @Test
    void infersSchemaCorrectlyWithOnlyAHeaderAndNoDataRows() throws Exception {
        String csv = "name,age\n";

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(2, schema.getColumns().size());
        assertEquals(DataType.STRING, schema.getColumns().get(0).getType());
        assertEquals(DataType.STRING, schema.getColumns().get(1).getType());
    }

    /*
     * =========================
     * Header edge cases
     * =========================
     */

    @Test
    void allowsEmptyHeaderAndRetainsItAsAColumnName() throws Exception {
        String csv = """
                name,,age
                Alice,hello,25
                """;

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(3, schema.getColumns().size());
        assertEquals("name", schema.getColumns().get(0).getName());
        assertEquals("", schema.getColumns().get(1).getName());
        assertEquals("age", schema.getColumns().get(2).getName());
    }

    @Test
    void preservesDuplicateHeaderNamesAsSeparateColumns() throws Exception {
        String csv = "name,name\nAlice,Smith\n";

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(2, schema.getColumns().size());
        assertEquals("name", schema.getColumns().get(0).getName());
        assertEquals("name", schema.getColumns().get(1).getName());
    }

    @Test
    void handlesSingleColumnCsv() throws Exception {
        String csv = "name\nAlice\nBob\n";

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(1, schema.getColumns().size());
        assertEquals("name", schema.getColumns().get(0).getName());
    }

    @Test
    void rejectsCsvWithNoHeaders() {
        String csv = "";

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parseSchema(input(csv))
        );
    }

    /*
     * =========================
     * Ragged rows / malformed data
     * =========================
     */

    @Test
    void treatsMissingTrailingValuesAsNullRatherThanFailing() throws Exception {
        // second row is short a column
        String csv = "name,age,active\nAlice,25,true\nBob,30\n";

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(3, schema.getColumns().size());
        // "active" only has one real value ("true") since Bob's row is missing it;
        // that alone should still be enough to infer BOOLEAN
        assertEquals(DataType.BOOLEAN, schema.getColumns().get(2).getType());
    }

    @Test
    void quotedFieldsWithEmbeddedCommasAreParsedAsSingleValues() throws Exception {
        String csv = "name,note\n\"Smith, John\",hello\n";

        CsvParseResult result = parser.parse(input(csv));

        assertEquals(2, result.schema().getColumns().size());
        assertEquals("Smith, John", result.previewRows().get(0).get(0));
    }

    /*
     * =========================
     * parse() - full result incl. preview rows
     * =========================
     */

    @Test
    void parseReturnsSchemaHeadersAndPreviewRowsFromOnePass() throws IOException {
        String csv = "name,age\nAlice,25\nBob,30\n";

        CsvParseResult result = parser.parse(input(csv));

        assertEquals(List.of("name", "age"), result.headers());
        assertEquals(2, result.schema().getColumns().size());
        assertEquals(2, result.previewRows().size());
        assertEquals("Alice", result.previewRows().get(0).get(0));
        assertEquals("25", result.previewRows().get(0).get(1));
    }

    @Test
    void parseCapsPreviewRowsAtTenEvenWithMoreRowsAvailable() throws IOException {
        StringBuilder csv = new StringBuilder("name,age\n");
        for (int i = 0; i < 50; i++) {
            csv.append("person").append(i).append(",").append(20 + i).append("\n");
        }

        CsvParseResult result = parser.parse(input(csv.toString()));

        assertEquals(CsvDatasetParser.PREVIEW_SAMPLE_SIZE, result.previewRows().size());
        assertEquals("person0", result.previewRows().get(0).get(0));
    }

    @Test
    void parseInfersTypeFromMoreRowsThanItReturnsForPreview() throws IOException {
        // 15 rows: type inference sees all of them (within the 100-row inference
        // window), even though only 10 come back as preview rows.
        StringBuilder csv = new StringBuilder("id\n");
        for (int i = 1; i <= 15; i++) {
            csv.append(i).append("\n");
        }

        CsvParseResult result = parser.parse(input(csv.toString()));

        assertEquals(DataType.INTEGER, result.schema().getColumns().get(0).getType());
        assertEquals(10, result.previewRows().size());
    }

    @Test
    void parseReturnsFewerThanTenPreviewRowsWhenFewerAreAvailable() throws IOException {
        String csv = "name,age\nAlice,25\n";

        CsvParseResult result = parser.parse(input(csv));

        assertEquals(1, result.previewRows().size());
    }

    @Test
    void parseAndParseSchemaAgreeOnSchemaForTheSameInput() throws IOException {
        // Regression guard for the old dual-parse bug: parseSchema() and
        // parse().schema() must never disagree about column types, since
        // they're expected to be backed by the same parsing logic
        String csv = "name,age,active\nAlice,25,true\nBob,30,false\n";

        DatasetSchema viaParseSchema = parser.parseSchema(input(csv));
        DatasetSchema viaParse = parser.parse(input(csv)).schema();

        assertEquals(viaParseSchema, viaParse);
    }

    @Test
    void parseThrowsOnEmptyInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(input(""))
        );
    }

    private ByteArrayInputStream input(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}