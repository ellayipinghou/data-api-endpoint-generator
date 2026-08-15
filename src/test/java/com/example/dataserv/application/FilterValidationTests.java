package com.example.dataserv.application;

import com.example.dataserv.domain.*;
import com.example.dataserv.storage.DatasetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FilterValidationTests {

    @Test
    void queryConvertsFilterValuesAndValidatesOperators() {
        DatasetRepository repo = mock(DatasetRepository.class);

        DatasetSchema schema = new DatasetSchema(List.of(
                new DataColumn("name", DataType.STRING),
                new DataColumn("age", DataType.INTEGER),
                new DataColumn("active", DataType.BOOLEAN)
        ));

        UUID id = UUID.randomUUID();
        Dataset dataset = new Dataset(id, "test", schema);

        when(repo.findById(id)).thenReturn(java.util.Optional.of(dataset));

        DatasetService service = new DatasetService(repo);

        List<Filter> filters = List.of(
                new Filter("age", FilterOperator.GREATER_THAN, "30"),
                new Filter("active", FilterOperator.EQUALS, "true"),
                new Filter("name", FilterOperator.LIKE, "Al%")
        );

        service.queryDataset(id, filters);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).query(eq(id), captor.capture());

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
