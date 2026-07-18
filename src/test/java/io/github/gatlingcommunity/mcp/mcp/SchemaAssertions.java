package io.github.gatlingcommunity.mcp.mcp;

import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SchemaAssertions {
    private SchemaAssertions() {
    }

    static void assertMatches(Map<String, Object> schema, Object value) {
        assertMatches(schema, value, "$");
    }

    @SuppressWarnings("unchecked")
    private static void assertMatches(Map<String, Object> schema, Object value, String path) {
        if (schema.containsKey("type")) {
            assertType(String.valueOf(schema.get("type")), value, path);
        }

        if (schema.get("required") instanceof List<?> required) {
            if (!(value instanceof Map<?, ?> object)) {
                fail("%s must be an object to satisfy required fields %s", path, required);
                return;
            }
            for (var field : required) {
                if (!object.containsKey(String.valueOf(field))) {
                    fail("%s is missing required field %s", path, field);
                }
            }
        }

        if ("object".equals(schema.get("type"))) {
            validateObject(schema, value, path);
        } else if ("array".equals(schema.get("type"))
                && schema.get("items") instanceof Map<?, ?> itemSchema
                && value instanceof List<?> items) {
            for (var i = 0; i < items.size(); i++) {
                assertMatches((Map<String, Object>) itemSchema, items.get(i), path + "[" + i + "]");
            }
        }

        if (schema.get("anyOf") instanceof List<?> alternatives) {
            var failures = new ArrayList<String>();
            for (var alternative : alternatives) {
                if (alternative instanceof Map<?, ?> alternativeSchema) {
                    try {
                        assertMatches((Map<String, Object>) alternativeSchema, value, path);
                        return;
                    } catch (AssertionError error) {
                        failures.add(error.getMessage());
                    }
                }
            }
            fail("%s must match at least one anyOf branch. Failures: %s", path, failures);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateObject(Map<String, Object> schema, Object value, String path) {
        if (!(value instanceof Map<?, ?> object)) {
            fail("%s must be an object", path);
            return;
        }
        var properties = schema.get("properties") instanceof Map<?, ?> rawProperties
                ? (Map<String, Object>) rawProperties
                : Map.<String, Object>of();

        for (var entry : object.entrySet()) {
            var key = String.valueOf(entry.getKey());
            if (properties.get(key) instanceof Map<?, ?> propertySchema) {
                assertMatches((Map<String, Object>) propertySchema, entry.getValue(), path + "." + key);
                continue;
            }
            var additionalProperties = schema.get("additionalProperties");
            if (Boolean.FALSE.equals(additionalProperties)) {
                fail("%s has unexpected field %s", path, key);
            }
            if (additionalProperties instanceof Map<?, ?> additionalSchema) {
                assertMatches((Map<String, Object>) additionalSchema, entry.getValue(), path + "." + key);
            }
        }
    }

    private static void assertType(String type, Object value, String path) {
        switch (type) {
            case "object" -> {
                if (!(value instanceof Map<?, ?>)) {
                    fail("%s must be object but was %s", path, typeName(value));
                }
            }
            case "array" -> {
                if (!(value instanceof List<?>)) {
                    fail("%s must be array but was %s", path, typeName(value));
                }
            }
            case "string" -> {
                if (!(value instanceof String)) {
                    fail("%s must be string but was %s", path, typeName(value));
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    fail("%s must be boolean but was %s", path, typeName(value));
                }
            }
            case "integer" -> {
                if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
                    fail("%s must be integer but was %s", path, typeName(value));
                }
            }
            case "number" -> {
                if (!(value instanceof Number)) {
                    fail("%s must be number but was %s", path, typeName(value));
                }
            }
            default -> fail("%s uses unsupported test schema type %s", path, type);
        }
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
