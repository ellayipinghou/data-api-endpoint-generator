//package com.example.dataserv.ingestion;
//
//import com.example.dataserv.domain.DataType;
//import org.junit.jupiter.api.Test;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//
//public class ConvertValueTests {
//    private final ConvertValue converter = new ConvertValue();
//
//    @Test
//    void convertsString() {
//        assertEquals("hello", converter.convertValue("hello", DataType.STRING));
//    }
//
//    @Test
//    void convertsInteger() {
//        assertEquals(42, converter.convertValue("42", DataType.INTEGER));
//    }
//
//    @Test
//    void convertsLong() {
//        assertEquals(42L, converter.convertValue("42", DataType.LONG));
//    }
//
//    @Test
//    void convertsDouble() {
//        assertEquals(42.5, converter.convertValue("42.5", DataType.DOUBLE));
//    }
//
//    @Test
//    void convertsBoolean() {
//        assertEquals(true, converter.convertValue("true", DataType.BOOLEAN));
//    }
//
//    @Test
//    void convertsDate() {
//        assertEquals(
//            LocalDate.of(2026, 8, 12),
//            converter.convertValue("2026-08-12", DataType.DATE)
//        );
//    }
//
//    @Test
//    void convertsDatetime() {
//        assertEquals(
//            LocalDateTime.of(2026, 8, 12, 15, 30),
//            converter.convertValue("2026-08-12T15:30", DataType.DATETIME)
//        );
//    }
//
//    @Test
//    void invalidIntegerThrowsException() {
//        assertThrows(
//            IllegalArgumentException.class,
//            () -> converter.convertValue("hello", DataType.INTEGER)
//        );
//    }
//}