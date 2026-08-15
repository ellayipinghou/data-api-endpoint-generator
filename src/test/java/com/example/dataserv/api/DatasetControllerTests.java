package com.example.dataserv.api;

import com.example.dataserv.application.DatasetService;
import com.example.dataserv.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DatasetController.class)
class DatasetControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatasetService service;

    @Test
    void getDatasetReturnsDatasetWhenFound()
            throws Exception {

        UUID id = UUID.randomUUID();

        Dataset dataset = testDataset(id);

        when(service.findDataset(id))
                .thenReturn(Optional.of(dataset));

        mockMvc.perform(
                        get("/datasets/{id}", id)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name")
                        .value("test-dataset"))
                .andExpect(jsonPath("$.rowCount").value(42))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-08-13T12:00:00Z"));

        verify(service).findDataset(id);
    }

    @Test
    void getDatasetReturnsNotFoundWhenDatasetDoesNotExist()
            throws Exception {

        UUID id = UUID.randomUUID();

        when(service.findDataset(id))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/datasets/{id}", id)
                )
                .andExpect(status().isNotFound());

        verify(service).findDataset(id);
    }

    @Test
    void deleteDatasetReturnsNoContent()
            throws Exception {

        UUID id = UUID.randomUUID();

        doNothing()
                .when(service)
                .deleteDataset(id);

        mockMvc.perform(
                        delete("/datasets/{id}", id)
                )
                .andExpect(status().isNoContent());

        verify(service).deleteDataset(id);
    }

    @Test
    void queryDatasetReturnsRows()
            throws Exception {

        UUID id = UUID.randomUUID();

        DataRow alice = dataRow("Alice", 25);
        DataRow bob = dataRow("Bob", 30);

        List<DataRow> rows =
                List.of(alice, bob);

        when(service.queryDataset(
                eq(id),
                any()
        )).thenReturn(rows);

        String requestBody = """
                [
                    {
                        "column": "age",
                        "operator": "GREATER_THAN",
                        "value": "20"
                    }
                ]
                """;

        mockMvc.perform(
                        post("/datasets/{id}/query", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()")
                        .value(2))
                .andExpect(jsonPath("$[0].values.name")
                        .value("Alice"))
                .andExpect(jsonPath("$[0].values.age")
                        .value(25))
                .andExpect(jsonPath("$[1].values.name")
                        .value("Bob"))
                .andExpect(jsonPath("$[1].values.age")
                        .value(30));

        verify(service).queryDataset(
                eq(id),
                any()
        );
    }

    @Test
    void queryDatasetPassesFiltersToService()
            throws Exception {

        UUID id = UUID.randomUUID();

        when(service.queryDataset(
                eq(id),
                any()
        )).thenReturn(List.of());

        String requestBody = """
                [
                    {
                        "column": "age",
                        "operator": "GREATER_THAN",
                        "value": "25"
                    },
                    {
                        "column": "name",
                        "operator": "EQUALS",
                        "value": "Alice"
                    }
                ]
                """;

        mockMvc.perform(
                        post("/datasets/{id}/query", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk());

        verify(service).queryDataset(
                eq(id),
                argThat(filters ->
                        filters.size() == 2
                                && filters.get(0).column()
                                .equals("age")
                                && filters.get(0).operator()
                                == FilterOperator.GREATER_THAN
                                && filters.get(0).value()
                                .equals("25")
                                && filters.get(1).column()
                                .equals("name")
                                && filters.get(1).operator()
                                == FilterOperator.EQUALS
                                && filters.get(1).value()
                                .equals("Alice")
                )
        );
    }

    @Test
    void queryDatasetWithEmptyFilterListReturnsRows()
            throws Exception {

        UUID id = UUID.randomUUID();

        DataRow alice = dataRow("Alice", 25);

        when(service.queryDataset(
                eq(id),
                any()
        )).thenReturn(List.of(alice));

        mockMvc.perform(
                        post("/datasets/{id}/query", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[]")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()")
                        .value(1))
                .andExpect(jsonPath("$[0].values.name")
                        .value("Alice"));

        verify(service).queryDataset(
                eq(id),
                argThat(List::isEmpty)
        );
    }

    @Test
    void getDatasetRejectsInvalidUuid()
            throws Exception {

        mockMvc.perform(
                        get("/datasets/not-a-uuid")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private Dataset testDataset(UUID id) {

        DatasetSchema schema =
                new DatasetSchema(List.of(
                        new DataColumn(
                                "name",
                                DataType.STRING
                        ),
                        new DataColumn(
                                "age",
                                DataType.INTEGER
                        )
                ));

        return new Dataset(
                id,
                "test-dataset",
                schema,
                42,
                Instant.parse("2026-08-13T12:00:00Z")
        );
    }

    private DataRow dataRow(
            String name,
            int age
    ) {
        LinkedHashMap<String, Object> values =
                new LinkedHashMap<>();

        values.put("name", name);
        values.put("age", age);

        return new DataRow(values);
    }
}
