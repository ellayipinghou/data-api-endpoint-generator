package com.example.dataserv.api;

public class PreviewIssue {
    private final String kind;
    private final String column;
    private final String message;
    private final Boolean isBlocking;

    public PreviewIssue(String kind, String column, String message, Boolean isBlocking) {
        this.kind = kind;
        this.column = column;
        this.message = message;
        this.isBlocking = isBlocking;
    }

    public String getKind() {
        return kind;
    }

    public String getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    public Boolean getIsBlocking() {
        return isBlocking;
    }
}
