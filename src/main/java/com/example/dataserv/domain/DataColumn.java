package com.example.dataserv.domain;

import java.util.Objects;
import java.util.List;

public class DataColumn {
    private final String name;
    private final DataType type;
    private static List<String> operators;

    // TODO: potentially add constraints

    public DataColumn(String name, DataType type) {
        this.name = name;
        this.type = type;
        this.operators = operatorsForType(type);
    }

    private List<String> operatorsForType(DataType type) {
        return switch (type) {
            case STRING -> List.of(
                "=", "!=", "CONTAINS"
            );
            case INTEGER, LONG, DOUBLE -> List.of(
                "=", "!=", "<", "<=", ">", ">="
            );
            case DATE, DATETIME -> List.of(
                "=", "!=", "<", "<=", ">", ">="
            );
            case BOOLEAN -> List.of(
                "=", "!="
            );
        };
    }

    public String getName() {
        return name;
    }

    public DataType getType() {
        return type;
    }

    public List<String> getOperators() {
        return operators;
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
        return Objects.equals(name, that.name) && type == that.type && operators.equals(that.operators);
    }

    @Override
    public int hashCode() {
        // create hash from name and type fields
        return Objects.hash(name, type, operators);
    }

    @Override
    public String toString() {
        return "DataColumn{" + "name='" + name + '\'' + ", type=" + type + '\'' + ", operators=" + operators + '}';
    }
}