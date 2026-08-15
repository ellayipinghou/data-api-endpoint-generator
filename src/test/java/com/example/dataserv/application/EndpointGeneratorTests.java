// package com.example.dataserv.application;

// import com.example.dataserv.domain.DataColumn;
// import com.example.dataserv.domain.DataType;
// import com.example.dataserv.domain.DatasetSchema;
// import org.junit.jupiter.api.Test;

// import java.util.List;

// import static org.junit.jupiter.api.Assertions.*;

// class EndpointGeneratorTests {

//     @Test
//     void generatesOperatorsForTypes() {
//         DatasetSchema schema = new DatasetSchema(List.of(
//                 new DataColumn("name", DataType.STRING),
//                 new DataColumn("age", DataType.INTEGER),
//                 new DataColumn("active", DataType.BOOLEAN),
//                 new DataColumn("created", DataType.DATE)
//         ));

//         String json = EndpointGenerator.generate(schema);

//         assertNotNull(json);
//         assertTrue(json.contains("\"column\":\"name\""));
//         assertTrue(json.contains("CONTAINS"));
//         assertTrue(json.contains("\"column\":\"age\""));
//         assertTrue(json.contains("<"));
//         assertTrue(json.contains("\"column\":\"active\""));
//         assertTrue(json.contains("IS NULL"));
//         assertTrue(json.contains("\"column\":\"created\""));
//         assertTrue(json.contains("BETWEEN"));
//     }
// }
