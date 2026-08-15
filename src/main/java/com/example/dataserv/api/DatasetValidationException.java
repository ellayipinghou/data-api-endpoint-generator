package com.example.dataserv.api;

import java.util.List;

public class DatasetValidationException extends RuntimeException {
    private final List<PreviewIssue> issues;

    public DatasetValidationException(String message, List<PreviewIssue> issues) {
        super(message);
        this.issues = issues;
    }

    public List<PreviewIssue> getIssues() {
        return issues;
    }
}
