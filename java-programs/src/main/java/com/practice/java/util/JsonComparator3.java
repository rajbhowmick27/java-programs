package com.practice.java.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonComparator3 {

    private static final ObjectMapper objectMapper = new ObjectMapper(); // Jackson ObjectMapper to work with JsonNode

    // Compare two JSON objects for the given list of field paths and return a JsonNode report
    public static JsonNode compareJson(JSONObject json1, JSONObject json2, List<String> fieldsToCompare) {
        Map<String, Object> differences = new HashMap<>();
        boolean isEqual = true;

        for (String path : fieldsToCompare) {
            isEqual &= compareNested(json1, json2, path, "", differences);
        }

        // Prepare the summary part
        Map<String, Object> summary = new HashMap<>();
        summary.put("message", "JSON Difference Report");
        summary.put("comparisonResult", isEqual ? "Equal" : "Not Equal");

        // Prepare the final structured JSON report
        Map<String, Object> report = new HashMap<>();
        report.put("summary", summary);
        report.put("differences", differences);

        return objectMapper.valueToTree(report); // Convert the report map to a JsonNode
    }

    // Recursively compare nested JSON objects and lists, including arrays with wildcards
    private static boolean compareNested(Object json1, Object json2, String path, String parentPath, Map<String, Object> differences) {
        boolean isEqual = true;

        // If the path is empty, we're at the leaf, just compare the values
        if (path.isEmpty()) {
            if (!json1.equals(json2)) {
                differences.put(parentPath, createDifferenceMap(json1, json2));
                isEqual = false;
            }
            return isEqual;
        }

        // If the path contains the wildcard [*], we need to handle arrays
        if (path.contains("[*]")) {
            String[] parts = path.split("\\[\\*\\]", 2);
            String arrayPath = parts[0]; // The part before [*], e.g., "users"
            String restOfPath = parts.length > 1 ? parts[1] : ""; // The part after [*], e.g., "address.city"

            if (restOfPath.startsWith(".")) {
                restOfPath = restOfPath.substring(1);
            }

            if (json1 instanceof JSONObject && json2 instanceof JSONObject) {
                JSONObject obj1 = (JSONObject) json1;
                JSONObject obj2 = (JSONObject) json2;

                if (obj1.has(arrayPath) && obj2.has(arrayPath)) {
                    Object array1 = obj1.get(arrayPath);
                    Object array2 = obj2.get(arrayPath);

                    if (array1 instanceof JSONArray && array2 instanceof JSONArray) {
                        JSONArray arr1 = (JSONArray) array1;
                        JSONArray arr2 = (JSONArray) array2;

                        // Iterate over each item in the arrays
                        int maxLength = Math.max(arr1.length(), arr2.length());
                        for (int i = 0; i < maxLength; i++) {
                            Object element1 = (i < arr1.length()) ? arr1.get(i) : null;
                            Object element2 = (i < arr2.length()) ? arr2.get(i) : null;

                            // Compare the elements in the arrays
                            isEqual &= compareNested(element1, element2, restOfPath, parentPath + arrayPath + "[" + i + "].", differences);
                        }
                    } else {
                        differences.put(parentPath + arrayPath, createDifferenceMap(array1, array2));
                        isEqual = false;
                    }
                } else {
                    differences.put(parentPath + arrayPath, createDifferenceMap(null, null));
                    isEqual = false;
                }
            }
            return isEqual;
        }

        // If we don't have the wildcard [*], we process as a standard key path
        String[] pathComponents = path.split("\\.");
        String currentKey = pathComponents[0]; // The key we are checking at this level
        String restOfPath = pathComponents.length > 1 ? pathComponents[1] : ""; // The rest of the path after the current key

        if (json1 instanceof JSONObject && json2 instanceof JSONObject) {
            JSONObject obj1 = (JSONObject) json1;
            JSONObject obj2 = (JSONObject) json2;

            if (obj1.has(currentKey) && obj2.has(currentKey)) {
                Object value1 = obj1.get(currentKey);
                Object value2 = obj2.get(currentKey);

                // Recurse with the next part of the path
                isEqual &= compareNested(value1, value2, restOfPath, parentPath + currentKey + ".", differences);
            } else {
                differences.put(parentPath + currentKey, createDifferenceMap(null, null));
                isEqual = false;
            }
        } else {
            if (!json1.equals(json2)) {
                differences.put(parentPath + currentKey, createDifferenceMap(json1, json2));
                isEqual = false;
            }
        }

        return isEqual;
    }

    // Helper method to create a map for a difference
    private static Map<String, Object> createDifferenceMap(Object value1, Object value2) {
        Map<String, Object> difference = new HashMap<>();
        difference.put("message", "Values are different");
        difference.put("expected", value1 != null ? value1.toString() : "null");
        difference.put("actual", value2 != null ? value2.toString() : "null");
        return difference;
    }

    // Main method to demonstrate the usage
    public static void main(String[] args) {
        // Example JSON objects to compare
        String jsonStr1 = "{\n" +
                "  \"users\": [\n" +
                "    {\n" +
                "      \"name\": \"John\",\n" +
                "      \"address\": {\n" +
                "        \"city\": \"New York\",\n" +
                "        \"zip\": \"10001\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Jane\",\n" +
                "      \"address\": {\n" +
                "        \"city\": \"Chicago\",\n" +
                "        \"zip\": \"60601\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"age\": 30\n" +
                "}";

        String jsonStr2 = "{\n" +
                "  \"users\": [\n" +
                "    {\n" +
                "      \"name\": \"John\",\n" +
                "      \"address\": {\n" +
                "        \"city\": \"Los Angeles\",\n" +
                "        \"zip\": \"10001\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"Jane\",\n" +
                "      \"address\": {\n" +
                "        \"city\": \"Chicago\",\n" +
                "        \"zip\": \"60601\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"age\": 31\n" +
                "}";

        JSONObject json1 = new JSONObject(jsonStr1);
        JSONObject json2 = new JSONObject(jsonStr2);

        // User-defined fields to compare (supporting wildcard in the path)
        List<String> fieldsToCompare = new ArrayList<>();
        fieldsToCompare.add("users[*].address.city");  // Compare the "city" field inside each object in the "users" array
        fieldsToCompare.add("age");  // Compare the "age" field

        // Compare JSON objects and get the structured JSON report
        JsonNode differences = compareJson(json1, json2, fieldsToCompare);

        // Output the differences as JSON
        System.out.println(differences.toString());
    }
}
