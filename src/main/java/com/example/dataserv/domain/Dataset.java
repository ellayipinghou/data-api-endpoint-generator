package com.example.dataserv.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Dataset {
    private final UUID id;
    private final String name;
    private final DatasetSchema schema;
    private final long rowCount;
    private final Instant createdAt;

    public Dataset(UUID id, String name, DatasetSchema schema) {
        this(id, name, schema, 0, Instant.EPOCH);
    }

    public Dataset(
            UUID id,
            String name,
            DatasetSchema schema,
            long rowCount,
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.schema = schema;
        this.rowCount = rowCount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DatasetSchema getSchema() {
        return schema;
    }

    public long getRowCount() {
        return rowCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dataset)) return false;

        Dataset dataset = (Dataset) o;
        return Objects.equals(id, dataset.id)
                && Objects.equals(name, dataset.name)
                && Objects.equals(schema, dataset.schema)
                && rowCount == dataset.rowCount
                && Objects.equals(createdAt, dataset.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, schema, rowCount, createdAt);
    }

    @Override
    public String toString() {
        return "Dataset{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", schema=" + schema +
                ", rowCount=" + rowCount +
                ", createdAt=" + createdAt +
                '}';
    }
}
