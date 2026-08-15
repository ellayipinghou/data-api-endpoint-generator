package com.example.dataserv.ingestion.csv;

import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.ingestion.DatasetParser;
import com.example.dataserv.ingestion.TypeInferer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CsvDatasetParser implements DatasetParser {

    private static final int INFERENCE_SAMPLE_SIZE = 100;

    @Override
    public DatasetSchema parseSchema(InputStream input) throws IOException {
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);

        try (CSVParser csvParser = CSVFormat.DEFAULT.parse(reader)) {
            Iterator<CSVRecord> records = csvParser.iterator();

            if (!records.hasNext()) {
                throw new IllegalArgumentException("CSV must contain a header row");
            }

            CSVRecord headerRecord = records.next();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRecord.size(); i++) {
                headers.add(headerRecord.get(i));
            }

            // We do not throw on blank header names here — preview should collect
            // issues instead of failing outright. Read sample rows from the
            // remaining iterator.
            List<CSVRecord> sampleRecords = readSample(records);

            List<DataColumn> columns = new ArrayList<>();

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                List<String> values = new ArrayList<>();

                for (CSVRecord record : sampleRecords) {
                    String v = null;
                    try {
                        v = record.get(i);
                    } catch (RuntimeException ignored) {
                        // missing value for this row/column -> null
                    }
                    values.add(v);
                }

                DataType type = TypeInferer.inferType(values);

                columns.add(new DataColumn(header, type));
            }

            return new DatasetSchema(columns);
        }
    }

    private List<CSVRecord> readSample(Iterator<CSVRecord> records) {
        List<CSVRecord> sampleRecords = new ArrayList<>();

        while (records.hasNext() && sampleRecords.size() < INFERENCE_SAMPLE_SIZE) {
            sampleRecords.add(records.next());
        }

        return sampleRecords;
    }

    private void validateHeaders(List<String> headers) {
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("CSV must contain a header row");
        }

        // Allow empty header names here so preview can report them as issues
        // instead of causing an immediate parse failure. Validation of empty
        // names is handled higher in the preview flow where issues are
        // accumulated and returned to the client.
    }
}