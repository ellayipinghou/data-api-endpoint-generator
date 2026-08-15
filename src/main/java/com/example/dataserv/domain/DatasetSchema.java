package com.example.dataserv.domain;


import java.util.List;
import java.util.Objects;

public class DatasetSchema {
    // immutable - schema can't change after creation
    // TODO: change this if we allow schema migrations for now  
    private final List<DataColumn> columns;

    // represent schema as a list of DataColumn objects
    public DatasetSchema(List<DataColumn> columns) {
        this.columns = columns;
    }

    public List<DataColumn> getColumns() {
        return columns;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DatasetSchema)) return false;

        DatasetSchema that = (DatasetSchema) o;
        return Objects.equals(columns, that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns);
    }

    @Override
    public String toString() {
        return "DatasetSchema{" + "columns=" + columns +'}';
    }
}
