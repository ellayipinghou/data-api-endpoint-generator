package com.example.dataserv.application;

import com.example.dataserv.domain.*;
import com.example.dataserv.ingestion.DatasetParser;
import com.example.dataserv.ingestion.csv.CsvDatasetParser;
import com.example.dataserv.storage.DatasetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// integration test for filter validation and value type conversion through queryDataset
class FilterValidationTests {

    @Test
    void queryConvertsFilterValuesAndValidatesOperators() {
        DatasetRepository repo = mock(DatasetRepository.class);
        DatasetParser parser = new CsvDatasetParser();
        // mocked - preview storage not actually needed by queryDataset
        PreviewStorage storage = mock(PreviewStorage.class);

        DatasetSchema schema = new DatasetSchema(List.of(
            new DataColumn("name", DataType.STRING),
            new DataColumn("age", DataType.INTEGER),
            new DataColumn("active", DataType.BOOLEAN)
        ));

        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test", schema);

        when(repo.findById(id)).thenReturn(java.util.Optional.of(dataset));

        DatasetService service = new DatasetService(repo, parser, storage);

        List<Filter> filters = List.of(
            new Filter("age", FilterOperator.GREATER_THAN, "30"),
            new Filter("active", FilterOperator.EQUALS, "true"),
            new Filter("name", FilterOperator.LIKE, "Al%")
        );

        service.queryDataset(id, filters);

        // verify that repo.query() was called with this dataset ID, capture the list of filters it received, and check that service converted the filter values correctly
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).query(eq(id), captor.capture());
        // Suppress the expected unchecked-conversion warning because ArgumentCaptor can only capture List.class, not List<Filter>
        @SuppressWarnings("unchecked")
        List<Filter> validated = captor.getValue();

        assertEquals(3, validated.size());

        assertEquals(Integer.class, validated.get(0).value().getClass());
        assertEquals(30, validated.get(0).value());

        assertEquals(Boolean.class, validated.get(1).value().getClass());
        assertEquals(true, validated.get(1).value());

        assertEquals(String.class, validated.get(2).value().getClass());
        assertEquals("Al%", validated.get(2).value());
    }
}
