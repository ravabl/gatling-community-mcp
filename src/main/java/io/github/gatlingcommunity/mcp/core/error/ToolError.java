package io.github.gatlingcommunity.mcp.core.error;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolError(
        String code,
        String severity,
        String path,
        String message,
        String suggestion,
        Map<String, Object> details
) {
    public ToolError {
        code = blankDefault(code, "tool.error");
        severity = blankDefault(severity, "error");
        path = blankDefault(path, "$");
        message = blankDefault(message, code);
        suggestion = blankDefault(suggestion, "Inspect the tool input and retry with supported arguments.");
        details = Map.copyOf(details == null ? Map.of() : details);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("code", code);
        values.put("severity", severity);
        values.put("path", path);
        values.put("message", message);
        values.put("suggestion", suggestion);
        if (!details.isEmpty()) {
            var safeDetails = new LinkedHashMap<String, Object>();
            details.forEach((key, value) -> safeDetails.put(key, String.valueOf(value)));
            values.put("details", Map.copyOf(safeDetails));
        }
        return Map.copyOf(values);
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
