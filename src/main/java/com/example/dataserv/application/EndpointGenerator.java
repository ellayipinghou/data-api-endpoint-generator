// package com.example.dataserv.application;

// import com.example.dataserv.domain.DataColumn;
// import com.example.dataserv.domain.DataType;
// import com.example.dataserv.domain.DatasetSchema;
// import java.util.StringJoiner;

// import java.util.ArrayList;
// import java.util.List;

// /**
//  * Generates a JSON representation of available endpoints/filters
//  * for a given dataset schema. The output is intentionally a
//  * simple array of objects describing column, type and allowed operators.
//  */
// public class EndpointGenerator {
//     public static String generate(DatasetSchema schema) {
//         // mutable sequence of characters used to modify strings efficiently (esp in loops)
//         StringBuilder sb = new StringBuilder();
//         sb.append("[");

//         // construct a sequence of characters separated by a delimiter
//         StringJoiner joiner = new StringJoiner(",");

//         // iterate through columns and build a json
//         /* ex. {
//                     "column": "column_name",
//                     "type": "DATA_TYPE",
//                     "operators": ["...", "..."]
//                 }
//          */
//         for (DataColumn column : schema.getColumns()) {
//             StringBuilder item = new StringBuilder();
//             item.append("{");
//             item.append("\"column\":\"")
//                 .append(escapeJson(column.getName()))
//                 .append("\"");
//             item.append(",\"type\":\"")
//                 .append(column.getType().name())
//                 .append("\"");

//             // operators array
//             item.append(",\"operators\":[");
//             List<String> operators = operatorsForType(column.getType());
//             StringJoiner opJoin = new StringJoiner(",");
//             for (String op : operators) {
//                 // escape, and wrap in double quotes
//                 opJoin.add("\"" + escapeJson(op) + "\"");
//             }
//             item.append(opJoin.toString());
//             item.append("]");

//             item.append("}");
//             joiner.add(item.toString());
//         }

//         sb.append(joiner.toString());
//         sb.append("]");

//         return sb.toString();
//     }

//     // prevents broken JSON syntax by escaping backslashes (\) and double quotes ("):
//     private static String escapeJson(String s) {
//         return s.replace("\\", "\\\\").replace("\"", "\\\"");
//     }

//     private static List<String> operatorsForType(DataType type) {
//         List<String> ops = new ArrayList<>();

//         // common
//         ops.add("=");
//         ops.add("!=");
//         ops.add("IS NULL");
//         ops.add("IS NOT NULL");

//         switch (type) {
//             case STRING -> {
//                 ops.add("CONTAINS");
//                 ops.add("STARTS_WITH");
//                 ops.add("ENDS_WITH");
//             }
//             case INTEGER, LONG, DOUBLE -> {
//                 ops.add("<");
//                 ops.add("<=");
//                 ops.add(">");
//                 ops.add(">=");
//             }
//             case BOOLEAN -> {
//                 // nothing extra
//             }
//             case DATE, DATETIME -> {
//                 ops.add("<");
//                 ops.add("<=");
//                 ops.add(">");
//                 ops.add(">=");
//                 ops.add("BETWEEN");
//             }
//             default -> {}
//         }

//         return ops;
//     }
// }
