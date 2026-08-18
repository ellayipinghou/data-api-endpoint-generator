package com.example.dataserv.application;

public class PreviewMissingException extends RuntimeException {

    public PreviewMissingException() {
        super("Your CSV is missing or expired. Please upload the CSV again.");
    }
}