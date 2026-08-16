package com.example.dataserv.storage;

import java.sql.SQLException;

/**
 * Thrown when bulk-loading data into a dataset's physical table fails
 * because the data itself is invalid for the target schema (e.g. a value
 * beyond the type-inference sample window doesn't actually fit the column's
 * type). Carries a message derived from the underlying database error so
 * it can be surfaced to the caller as an actionable 400, rather than a
 * generic 500.
 */
public class DatasetLoadException extends SQLException {
    public DatasetLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}