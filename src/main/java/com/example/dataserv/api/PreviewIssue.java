package com.example.dataserv.api;

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

    public String getMessage() {
        return message;
    }
}