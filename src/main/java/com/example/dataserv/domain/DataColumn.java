package com.example.dataserv.domain;

import java.util.Objects;

public class DataColumn {
    private String name;
    private DataType type;

    // TODO: potentially add constraints

    public DataColumn(String name, DataType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        // check if it's exactly the same
        if (this == o) return true;
        // check if it's a DataColumn
        if (!(o instanceof DataColumn)) return false;

        // convert from object to DataColumn
        DataColumn that = (DataColumn) o;
        // make sure the fields are the same
        return Objects.equals(name, that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        // create hash from name and type fields
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "DataColumn{" + "name='" + name + '\'' + ", type=" + type + '}';
    }
}