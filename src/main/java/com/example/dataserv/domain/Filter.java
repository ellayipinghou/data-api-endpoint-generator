package com.example.dataserv.domain;

public record Filter(
    String column,
    FilterOperator operator,
    Object value
) {
}