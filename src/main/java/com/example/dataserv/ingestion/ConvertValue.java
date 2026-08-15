//package com.example.dataserv.ingestion;
//
//import com.example.dataserv.domain.DataType;
//
//public class ConvertValue {
//    public static Object convertValue(String value, DataType type) {
//
//        // Missing values become null.
//        if (value == null || value.isBlank()) {
//            return null;
//        }
//
//        try {
//            return switch (type) {
//                case STRING -> value;
//                case INTEGER -> Integer.valueOf(value);
//                case LONG -> Long.valueOf(value);
//                case DOUBLE -> Double.valueOf(value);
//                case BOOLEAN -> Boolean.parseBoolean(value);
//                case DATE -> java.time.LocalDate.parse(value);
//                case DATETIME -> java.time.LocalDateTime.parse(value);
//            };
//
//        } catch (RuntimeException e) {
//            throw new IllegalArgumentException("Value '" + value + "' could not be converted to " + type, e);
//        }
//    }
//}
