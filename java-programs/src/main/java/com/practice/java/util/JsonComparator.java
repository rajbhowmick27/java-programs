package com.practice.java.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

public class JsonComparator {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonNodeFactory jsonNodeFactory = JsonNodeFactory.instance;

    public JsonNode compareJsonObjects(String json1, String json2) throws Exception {
        JsonNode node1 = objectMapper.readTree(json1);
        JsonNode node2 = objectMapper.readTree(json2);

        ObjectNode report = jsonNodeFactory.objectNode();
        report.set("summary", jsonNodeFactory.objectNode()
                .put("message", "JSON Difference Report")
                .put("comparisonResult", areJsonEqual(node1, node2) ? "Equal" : "Not Equal"));

        if (node1.isObject() && node2.isObject()) {
            ObjectNode differences = compareObjects((ObjectNode) node1, (ObjectNode) node2, "");
            if (differences.size() > 0) {
                report.set("differences", differences);
            }
        } else if (node1.isArray() && node2.isArray()) {
            // Basic array comparison, can be enhanced for more detailed reporting
            if (!node1.toString().equals(node2.toString())) {
                report.set("difference", jsonNodeFactory.objectNode()
                        .put("message", "Arrays are different")
                        .setAll(Map.of("expected",node1,"actual",node2))
                        );
            }
        } else {
            report.set("difference", jsonNodeFactory.objectNode()
                    .put("message", "JSON types are different or not supported for detailed comparison")
                    .setAll(Map.of("expected",node1,"actual",node2))
            );
        }

        return report;
    }

    private boolean areJsonEqual(JsonNode node1, JsonNode node2) {
        return node1.toString().equals(node2.toString());
    }

    private ObjectNode compareObjects(ObjectNode node1, ObjectNode node2, String path) {
        ObjectNode differences = jsonNodeFactory.objectNode();

        Iterator<Map.Entry<String, JsonNode>> fields1 = node1.fields();
        Iterator<Map.Entry<String, JsonNode>> fields2 = node2.fields();

        // Find common fields
        while (fields1.hasNext()) {
            Map.Entry<String, JsonNode> field1 = fields1.next();
            JsonNode node2Field = node2.get(field1.getKey());
            if (node2Field != null) {
                if (field1.getValue().isObject() && node2Field.isObject()) {
                    ObjectNode objectDifferences = compareObjects((ObjectNode) field1.getValue(), (ObjectNode) node2Field, path + field1.getKey() + ".");
                    if (objectDifferences.size() > 0) {
                        differences.set(path + field1.getKey(), objectDifferences);
                    }
                } else if (field1.getValue().isArray() && node2Field.isArray()) {
                    // Basic array comparison, can be enhanced
                    if (!field1.getValue().toString().equals(node2Field.toString())) {
                        differences.set(path + field1.getKey(), jsonNodeFactory.objectNode()
                                .put("message", "Arrays are different")
                                .setAll(Map.of("expected", field1.getValue(),"actual", node2Field))
                                );
                    }
                } else if (!field1.getValue().toString().equals(node2Field.toString())) {
                    differences.set(path + field1.getKey(), jsonNodeFactory.objectNode()
                            .put("message", "Values are different")
                            .put("expected", field1.getValue().asText())
                            .put("actual", node2Field.asText()));
                }
            } else {
                differences.set(path + field1.getKey(), jsonNodeFactory.objectNode()
                        .put("message", "Field is missing in the second JSON")
                        .set("expected", field1.getValue()));
            }
        }

        // Find fields in node2 but not in node1
        while (fields2.hasNext()) {
            Map.Entry<String, JsonNode> field2 = fields2.next();
            if (node1.get(field2.getKey()) == null) {
                differences.set(path + field2.getKey(), jsonNodeFactory.objectNode()
                        .put("message", "Field is present in the second JSON but missing in the first JSON")
                        .set("actual", field2.getValue()));
            }
        }

        return differences;
    }

    public static void main(String[] args) throws Exception {
        JsonComparator comparator = new JsonComparator();

        // Example JSON strings
        String json1 = "{\"name\":\"John\",\"age\":30,\"address\":{\"street\":\"123 Main St\",\"city\":\"Anytown\"}}";
        String json2 = "{\"name\":\"Jane\",\"age\":30,\"address\":{\"street\":\"456 Elm St\",\"city\":\"Othertown\"}}";

        JsonNode res = comparator.compareJsonObjects(json1, json2);
        System.out.println(res.get("summary").get("comparisonResult").toString());
        System.out.println(res.toString());
    }

}



