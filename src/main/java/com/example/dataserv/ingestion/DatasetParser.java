package com.example.dataserv.ingestion;

import com.example.dataserv.domain.DatasetSchema;

import java.io.IOException;
import java.io.InputStream;

// TODO: later take delimiters, other options, etc
public interface DatasetParser {
    // implemented by CsvDatasetParser, etc
    ParseResult parse(InputStream input) throws IOException;

    // calls the class's own parse() and pulls the .schema() field off the result
    default DatasetSchema parseSchema(InputStream input) throws IOException {
        return parse(input).schema();
    }
}