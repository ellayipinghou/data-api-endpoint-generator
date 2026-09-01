package com.example.dataserv.ingestion.csv;

import com.example.dataserv.domain.DataColumn;
import com.example.dataserv.domain.DataRow;
import com.example.dataserv.domain.DataType;
import com.example.dataserv.domain.DatasetSchema;
import com.example.dataserv.ingestion.DatasetParser;
import com.example.dataserv.ingestion.ParseResult;
import com.example.dataserv.ingestion.TypeInferer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Component // needs to be a bean so Spring can inject it as the DatasetParser
public class CsvDatasetParser implements DatasetParser {

    static final int INFERENCE_SAMPLE_SIZE = 100;
    static final int PREVIEW_SAMPLE_SIZE = 10;

    public ParseResult parse(InputStream input) throws IOException {
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);

        try (CSVParser csvParser = CSVFormat.DEFAULT.parse(reader)) {
            Iterator<CSVRecord> records = csvParser.iterator();

            if (!records.hasNext()) {
                throw new IllegalArgumentException("CSV must contain a header row");
            }

            CSVRecord headerRecord = records.next();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRecord.size(); i++) {
                String h = headerRecord.get(i);
                headers.add(h == null ? null : h.trim());
            }

            int readSize = Math.max(INFERENCE_SAMPLE_SIZE, PREVIEW_SAMPLE_SIZE);
            List<CSVRecord> sampleRecords = readSample(records, readSize);

            Map<String, List<DataType>> typeOptions = new LinkedHashMap<>();
            List<DataColumn> columns = inferColumns(headers, sampleRecords, typeOptions);
            DatasetSchema schema = new DatasetSchema(columns);

            List<CSVRecord> previewRecords = sampleRecords.size() > PREVIEW_SAMPLE_SIZE
                ? sampleRecords.subList(0, PREVIEW_SAMPLE_SIZE)
                : sampleRecords;

            return new ParseResult(schema, headers, toDataRows(headers, previewRecords), typeOptions);
        }
    }

    private List<DataRow> toDataRows(List<String> headers, List<CSVRecord> records) {
        List<DataRow> rows = new ArrayList<>();
        for (CSVRecord record : records) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                values.put(headers.get(i), cellValue(record, i));
            }
            rows.add(new DataRow(values));
        }
        return rows;
    }

    private List<DataColumn> inferColumns(List<String> headers, List<CSVRecord> sampleRecords, Map<String, List<DataType>> typeOptionsOut) {
        List<DataColumn> columns = new ArrayList<>();

        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            List<String> values = new ArrayList<>();

            for (CSVRecord record : sampleRecords) {
                values.add(cellValue(record, i));
            }

            Set<DataType> candidates = TypeInferer.validRetypeCandidates(values);
            DataType type = TypeInferer.inferType(values);

            columns.add(new DataColumn(header, type));
            typeOptionsOut.put(header, List.copyOf(candidates));
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

    // no longer needs to be public - only CsvDatasetParser itself uses it now
    private static String cellValue(CSVRecord record, int index) {
        try {
            String v = record.get(index);
            return v == null ? null : v.trim();
        } catch (RuntimeException e) {
            return null;
        }
    }
}