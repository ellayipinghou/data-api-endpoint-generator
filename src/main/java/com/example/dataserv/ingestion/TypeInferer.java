package com.example.dataserv.ingestion;

import com.example.dataserv.domain.DataType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class TypeInferer {
    public static DataType inferType(List<String> values) {
        // "compatible with any type" (empty) defaults to string
        boolean hasValue = values.stream().anyMatch(v -> v != null && !v.isBlank());
        if (!hasValue) {
            return DataType.STRING;
        }
        Set<DataType> candidates = validRetypeCandidates(values);
        return pickHighestPriority(candidates);
    }

    /**
     * Returns every DataType that these values could be safely reinterpreted
     * as. STRING is always included, since any value can be stored as text.
     * If there are no non-blank values, every type is considered valid since
     * there is nothing to contradict any of them.
     */
    public static Set<DataType> validRetypeCandidates(List<String> values) {
        boolean canBeInteger = true;
        boolean canBeLong = true;
        boolean canBeDouble = true;
        boolean canBeBoolean = true;
        boolean canBeDate = true;
        boolean canBeDatetime = true;

        boolean foundValue = false;

        for (String value : values) {
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

        if (!foundValue) {
            return EnumSet.allOf(DataType.class);
        }

        Set<DataType> candidates = EnumSet.of(DataType.STRING);
        if (canBeInteger) candidates.add(DataType.INTEGER);
        if (canBeLong) candidates.add(DataType.LONG);
        if (canBeDouble) candidates.add(DataType.DOUBLE);
        if (canBeBoolean) candidates.add(DataType.BOOLEAN);
        if (canBeDate) candidates.add(DataType.DATE);
        if (canBeDatetime) candidates.add(DataType.DATETIME);
        return candidates;
    }

    private static DataType pickHighestPriority(Set<DataType> candidates) {
        // Check more specific types before more general types.
        // For example, "20" can technically be parsed as a Double,
        // but we want INTEGER.
        if (candidates.contains(DataType.INTEGER)) return DataType.INTEGER;
        if (candidates.contains(DataType.LONG)) return DataType.LONG;
        if (candidates.contains(DataType.DOUBLE)) return DataType.DOUBLE;
        if (candidates.contains(DataType.BOOLEAN)) return DataType.BOOLEAN;
        if (candidates.contains(DataType.DATE)) return DataType.DATE;
        if (candidates.contains(DataType.DATETIME)) return DataType.DATETIME;
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