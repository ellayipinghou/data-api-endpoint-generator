package com.example.dataserv.domain;

import java.util.Map;
import java.util.Objects;

public class DataRow {
    private Map<String, Object> values;

    public DataRow(Map<String, Object> values) {
        this.values = values;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataRow)) return false;

        DataRow dataRow = (DataRow) o;
        return Objects.equals(values, dataRow.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "DataRow{" +
                "values=" + values +
                '}';
    }
}
