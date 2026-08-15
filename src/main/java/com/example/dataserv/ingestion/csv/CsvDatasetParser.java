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
    static final int PREVIEW_SAMPLE_SIZE = 10;

    /**
     * Single-pass parse: reads the header once and reads up to
     * max(INFERENCE_SAMPLE_SIZE, PREVIEW_SAMPLE_SIZE) rows once, using
     * them both for type inference and as the preview sample. Callers
     * needing only the schema can use {@link #parseSchema}; callers
     * needing schema + preview rows should use this method instead of
     * re-parsing the input themselves.
     */
    public CsvParseResult parse(InputStream input) throws IOException {
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

            // Read enough rows to cover both type inference and the preview
            // sample in one pass; each consumer below just takes the slice
            // it needs from this single list.
            int readSize = Math.max(INFERENCE_SAMPLE_SIZE, PREVIEW_SAMPLE_SIZE);
            List<CSVRecord> sampleRecords = readSample(records, readSize);

            List<DataColumn> columns = inferColumns(headers, sampleRecords);
            DatasetSchema schema = new DatasetSchema(columns);

            List<CSVRecord> previewRows = sampleRecords.size() > PREVIEW_SAMPLE_SIZE
                    ? sampleRecords.subList(0, PREVIEW_SAMPLE_SIZE)
                    : sampleRecords;

            return new CsvParseResult(schema, headers, previewRows);
        }
    }

    @Override
    public DatasetSchema parseSchema(InputStream input) throws IOException {
        return parse(input).schema();
    }

    private List<DataColumn> inferColumns(List<String> headers, List<CSVRecord> sampleRecords) {
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

        return columns;
    }

    private List<CSVRecord> readSample(Iterator<CSVRecord> records, int limit) {
        List<CSVRecord> sampleRecords = new ArrayList<>();

        while (records.hasNext() && sampleRecords.size() < limit) {
            sampleRecords.add(records.next());
        }

        return sampleRecords;
    }
}