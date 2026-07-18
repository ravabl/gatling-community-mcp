package io.github.gatlingcommunity.mcp.mcp.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonSchemaBuilder {
    private JsonSchemaBuilder() {
    }

    public static Map<String, Object> object(List<String> required, Map<String, Object> properties) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        var requiredFields = List.copyOf(required == null ? List.of() : required);
        if (!requiredFields.isEmpty()) {
            schema.put("required", requiredFields);
        }
        schema.put("properties", Map.copyOf(properties == null ? Map.of() : properties));
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    public static Map<String, Object> objectWithAdditionalProperties(String description,
                                                                     Map<String, Object> additionalPropertiesSchema) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("description", description);
        schema.put("additionalProperties", Map.copyOf(additionalPropertiesSchema));
        return Map.copyOf(schema);
    }

    public static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    public static Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    public static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    public static Map<String, Object> number(String description) {
        return Map.of("type", "number", "description", description);
    }

    public static Map<String, Object> array(String description, Map<String, Object> itemSchema) {
        return Map.of(
                "type", "array",
                "description", description,
                "items", Map.copyOf(itemSchema)
        );
    }

    public static Map<String, Object> stringArray(String description) {
        return array(description, string("value"));
    }

    public static Map<String, Object> enumValues(String description, String... values) {
        return Map.of(
                "type", "string",
                "description", description,
                "enum", List.of(values)
        );
    }
}
