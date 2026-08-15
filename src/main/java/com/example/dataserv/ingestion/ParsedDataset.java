//package com.example.dataserv.ingestion;
//
//import com.example.dataserv.domain.DataRow;
//import com.example.dataserv.domain.DatasetSchema;
//
//import java.util.Iterator;
//
//public class ParsedDataset {
//    private final DatasetSchema schema;
//    private final Iterator<DataRow> rows;
//
//    public ParsedDataset(DatasetSchema schema, Iterator<DataRow> rows) {
//        this.schema = schema;
//        this.rows = rows;
//    }
//
//    public DatasetSchema getSchema() {
//        return schema;
//    }
//
//    public Iterator<DataRow> getRows() {
//        return rows;
//    }
//}