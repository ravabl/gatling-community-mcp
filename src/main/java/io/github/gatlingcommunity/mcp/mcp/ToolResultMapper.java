package io.github.gatlingcommunity.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ToolResultMapper {
    private static final Set<String> REQUIRED_ERROR_FIELDS = Set.of(
            "code", "severity", "path", "message", "suggestion");

    public record ToolResponse(String text, boolean error, Map<String, Object> structured) {
        public ToolResponse {
            text = text == null ? "" : text;
            structured = structured == null ? Map.of() : Map.copyOf(structured);
        }
    }

    public ToolResponse ok(String text, Map<String, Object> structured) {
        return new ToolResponse(text, false, structured);
    }

    public ToolResponse error(String text, Map<String, Object> structured) {
        return new ToolResponse(text, true, normalizeError(text, structured));
    }

    public McpSchema.CallToolResult toSdk(ToolResponse response) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(response.text())
                .isError(response.error())
                .structuredContent(response.structured())
                .build();
    }

    private static Map<String, Object> normalizeError(String text, Map<String, Object> structured) {
        var input = structured == null ? Map.<String, Object>of() : structured;
        if (input.keySet().containsAll(REQUIRED_ERROR_FIELDS)) {
            return input;
        }
        var values = new LinkedHashMap<String, Object>();
        values.put("code", stringValue(input.get("code"), "tool.error"));
        values.put("severity", stringValue(input.get("severity"), "error"));
        values.put("path", stringValue(input.get("path"), "$"));
        values.put("message", stringValue(input.get("message"), text == null || text.isBlank() ? "Tool call failed" : text));
        values.put("suggestion", stringValue(input.get("suggestion"),
                "Inspect the tool input and retry with supported arguments."));
        if (!input.isEmpty()) {
            values.put("details", stringify(input));
        }
        return Map.copyOf(values);
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        var text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private static Map<String, Object> stringify(Map<String, Object> values) {
        var details = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> details.put(key, stringifyValue(value)));
        return Map.copyOf(details);
    }

    private static String stringifyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.toString();
        }
        if (value instanceof List<?> list) {
            return list.toString();
        }
        return String.valueOf(value);
    }
}
