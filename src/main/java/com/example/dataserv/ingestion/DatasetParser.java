package com.example.dataserv.ingestion;

import com.example.dataserv.domain.DatasetSchema;

import java.io.IOException;
import java.io.InputStream;

// TODO: later take delimeters, other options, etc
public interface DatasetParser {
    DatasetSchema parseSchema(InputStream input) throws IOException;
}