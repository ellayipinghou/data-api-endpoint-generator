package com.example.dataserv.ingestion.csv;

import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CsvDatasetParserTests {

    private final CsvDatasetParser parser = new CsvDatasetParser();

    @Test
    void infersSchemaFromCsv() throws Exception {
        String csv = """
                name,age,score,active,birthday
                Alice,25,95.5,true,2020-01-15
                Bob,30,87.2,false,1999-05-20
                """;

        DatasetSchema schema = parser.parseSchema(input(csv));

        assertEquals(5, schema.getColumns().size());

        assertEquals(
                DataType.STRING,
                schema.getColumns().get(0).getType()
        );

        assertEquals(
                DataType.INTEGER,
                schema.getColumns().get(1).getType()
        );

        assertEquals(
                DataType.DOUBLE,
                schema.getColumns().get(2).getType()
        );

        assertEquals(
                DataType.BOOLEAN,
                schema.getColumns().get(3).getType()
        );

        assertEquals(
                DataType.DATE,
                schema.getColumns().get(4).getType()
        );
    }

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
    void rejectsCsvWithNoHeaders() {
        String csv = "";

        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parseSchema(input(csv))
        );
    }

    private ByteArrayInputStream input(String csv) {
        return new ByteArrayInputStream(
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }
}