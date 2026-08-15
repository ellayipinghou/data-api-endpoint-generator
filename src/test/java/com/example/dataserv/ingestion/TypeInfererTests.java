package com.example.dataserv.ingestion;

import com.example.dataserv.domain.DataType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeInfererTests {

    private final TypeInferer inferer = new TypeInferer();

    @Test
    void infersInteger() {
        assertEquals(DataType.INTEGER, inferer.inferType(List.of("1", "2", "3", "100")));
    }

    @Test
    void infersLong() {
        assertEquals(DataType.LONG, inferer.inferType(List.of("2147483648", "3000000000")));
    }

    @Test
    void infersDouble() {
        assertEquals(DataType.DOUBLE, inferer.inferType(List.of("1.5", "2.7", "3.14")));
    }

    @Test
    void infersBoolean() {
        assertEquals(DataType.BOOLEAN, inferer.inferType(List.of("true", "false", "true")));
    }

    @Test
    void infersDate() {
        assertEquals(DataType.DATE,
            inferer.inferType(List.of(
                "2026-01-01",
                "2026-02-15",
                "2026-08-12"
            ))
        );
    }

    @Test
    void infersDateTime() {
        assertEquals(
            DataType.DATETIME,
            inferer.inferType(List.of("2026-01-01T10:30:00", "2026-02-15T15:45:00"))
        );
    }

    @Test
    void infersString() {
        assertEquals(DataType.STRING, inferer.inferType(List.of("Alice", "Bob", "hello")));
    }

    @Test
    void mixedBecomesString() {
        assertEquals(DataType.STRING, inferer.inferType(List.of("1", "2026-01-01T10:30:00", "hello")));
    }

    @Test
    void ignoresEmptyValues() {
        assertEquals(DataType.INTEGER, inferer.inferType(List.of("1", "", "3")));
    }

    @Test
    void ignoresNullValues() {
        assertEquals(DataType.INTEGER, inferer.inferType(Arrays.asList("1", null, "3")));
    }

    @Test
    void choosesLongWhenMixOfLongAndInt() {
        assertEquals(DataType.LONG, inferer.inferType(List.of("1", "2147483648", "3")));
    }
}