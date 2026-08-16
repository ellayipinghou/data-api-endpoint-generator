package com.example.dataserv.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PreviewIssue {
    private final PreviewIssueKind kind;
    private final String column;
    private final String message;

    public PreviewIssue(PreviewIssueKind kind, String column, String message) {
        this.kind = kind;
        this.column = column;
        this.message = message;
    }

    public PreviewIssueKind getKind() {
        return kind;
    }

    @JsonProperty("isBlocking")
    public boolean isBlocking() {
        return kind.isBlocking();
    }

    public String getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }
}