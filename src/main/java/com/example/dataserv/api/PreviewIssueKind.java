package com.example.dataserv.api;

public enum PreviewIssueKind {
    HEADER_SUSPECTED_DATA_ROW(false),
    EMPTY_COLUMN_NAME(true),
    DUPLICATE_COLUMN_NAME(true),
    INVALID_COLUMN_NAME(true);

    private final boolean blocking;

    PreviewIssueKind(boolean blocking) {
        this.blocking = blocking;
    }

    public boolean isBlocking() {
        return blocking;
    }
}