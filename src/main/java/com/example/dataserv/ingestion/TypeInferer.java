package com.example.dataserv.ingestion;

import com.example.dataserv.domain.DataType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TypeInferer {

    public static DataType inferType(List<String> values) {
        boolean canBeInteger = true;
        boolean canBeLong = true;
        boolean canBeDouble = true;
        boolean canBeBoolean = true;
        boolean canBeDate = true;
        boolean canBeDatetime = true;

        boolean foundValue = false;

        for (String value : values) {
            /*
             * Treat empty values as compatible with any type.
             * We don't want one missing value to force an entire
             * column to STRING.
             */
            if (value == null || value.isBlank()) {
                continue;
            }

            foundValue = true;

            if (!canParseInteger(value)) canBeInteger = false;
            if (!canParseLong(value)) canBeLong = false;
            if (!canParseDouble(value)) canBeDouble = false;
            if (!canParseBoolean(value)) canBeBoolean = false;
            if (!canParseDate(value)) canBeDate = false;
            if (!canParseDatetime(value)) canBeDatetime = false;
        }

        // If the column has no non-empty values, default to STRING
        if (!foundValue) return DataType.STRING;

        /*
         * Check more specific types before more general types
         *
         * For example, "20" can technically be parsed as a Double,
         * but we want INTEGER
         */
        if (canBeInteger) return DataType.INTEGER;
        if (canBeLong) return DataType.LONG;
        if (canBeDouble) return DataType.DOUBLE;
        if (canBeBoolean) return DataType.BOOLEAN;
        if (canBeDate) return DataType.DATE;
        if (canBeDatetime) return DataType.DATETIME;

        /*
         * If values don't consistently match a known type,
         * treat the column as STRING.
         */
        return DataType.STRING;
    }

    private static boolean canParseInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean canParseLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean canParseDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean canParseBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }

    private static boolean canParseDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canParseDatetime(String value) {
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}