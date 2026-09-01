package com.example.dataserv.application;

import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;

import java.util.List;
import java.util.Map;

public record PreviewMetadata(
    DatasetSchema schema,
    Map<String, List<DataType>> typeOptions
) {}