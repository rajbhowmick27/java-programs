package com.practice.java.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JsonComparator2 {

    // Compare two JSON objects for the given list of field paths
    public static List<String> compareJson(JSONObject json1, JSONObject json2, List<String> fieldsToCompare) {
        List<String> differences = new ArrayList<>();
        for (String path : fieldsToCompare) {
            compareNested(json1, json2, path, "", differences);
        }
        return differences;
    }

    // Recursively compare nested JSON objects and lists, including arrays with wildcards
    private static void compareNested(Object json1, Object json2, String path, String parentPath, List<String> differences) {
        // If the path is empty, we're at the leaf, just compare the values
        if (path.isEmpty()) {
            if (!json1.equals(json2)) {
                differences.add("Difference found at path '" + parentPath + "': " + json1 + " != " + json2);
            }
            return;
        }

        // If the path contains the wildcard [*], we need to handle arrays
        String s1 = !parentPath.isBlank() ? parentPath + "." : "";
        if (path.contains("[*]")) {
            // Split path by [*], for example: "users[*].address.city" -> ["users", "address.city"]
            String[] parts = path.split("\\[\\*\\]");
            String arrayPath = parts[0]; // The part before [*], e.g., "users"
            String restOfPath = parts.length > 1 ? parts[1] : ""; // The part after [*], e.g., "address.city"

            if(restOfPath.startsWith(".")){
                restOfPath = restOfPath.substring(1);
            }
            // Process the array at the arrayPath
            if (json1 instanceof JSONObject && json2 instanceof JSONObject) {
                JSONObject obj1 = (JSONObject) json1;
                JSONObject obj2 = (JSONObject) json2;

                // Ensure both JSON objects have the array field at arrayPath
                String s = s1;
                if (obj1.has(arrayPath) && obj2.has(arrayPath)) {
                    Object array1 = obj1.get(arrayPath);
                    Object array2 = obj2.get(arrayPath);

                    // Ensure both are arrays
                    if (array1 instanceof JSONArray && array2 instanceof JSONArray) {
                        JSONArray arr1 = (JSONArray) array1;
                        JSONArray arr2 = (JSONArray) array2;

                        // Iterate over each item in the arrays
                        int maxLength = Math.max(arr1.length(), arr2.length());
                        for (int i = 0; i < maxLength; i++) {
                            Object element1 = (i < arr1.length()) ? arr1.get(i) : null;
                            Object element2 = (i < arr2.length()) ? arr2.get(i) : null;

                            // Compare the elements in the arrays by passing the rest of the path (e.g., "address.city")
                            compareNested(element1, element2, restOfPath, s + arrayPath + "[" + i + "]", differences);
                        }
                    } else {
                        differences.add("Expected an array at path '" + s + arrayPath + "', but found different types.");
                    }
                } else {
                    differences.add("Key '" + arrayPath + "' is missing in one of the JSON objects at path '" + s + arrayPath + "'");
                }
            }
            return;
        }

        // If we don't have the wildcard [*], we process as a standard key path
        String[] pathComponents = path.split("\\.");
        String currentKey = pathComponents[0]; // The key we are checking at this level
        String restOfPath = pathComponents.length > 1 ? pathComponents[1] : ""; // The rest of the path after the current key

        // If dealing with objects, compare based on the key
        if (json1 instanceof JSONObject && json2 instanceof JSONObject) {
            JSONObject obj1 = (JSONObject) json1;
            JSONObject obj2 = (JSONObject) json2;

            String k = s1;
            // Ensure the key exists in both JSON objects
            if (obj1.has(currentKey) && obj2.has(currentKey)) {
                Object value1 = obj1.get(currentKey);
                Object value2 = obj2.get(currentKey);

                // Recurse with the next part of the path
                compareNested(value1, value2, restOfPath, k + currentKey, differences);
            } else {
                differences.add("Key '" + currentKey + "' is missing in one of the JSON objects at path '" + k + currentKey + "'");
            }
        } else {
            // If it's a leaf node, compare values directly
            if (!json1.equals(json2)) {
                differences.add("Difference found at path '" + s1 + currentKey + "': " + json1 + " != " + json2);
            }
        }
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

        // Compare JSON objects
        List<String> differences = compareJson(json1, json2, fieldsToCompare);

        // Output differences
        if (differences.isEmpty()) {
            System.out.println("No differences found in the specified paths.");
        } else {
            System.out.println("Differences found:");
            for (String diff : differences) {
                System.out.println(diff);
            }
        }
    }
}












