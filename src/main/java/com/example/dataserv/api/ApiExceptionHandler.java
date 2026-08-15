package com.example.dataserv.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DatasetValidationException.class)
    public ResponseEntity<Map<String, Object>> handleDatasetValidationException(
            DatasetValidationException ex
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("issues", ex.getIssues());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
