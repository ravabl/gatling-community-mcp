package io.github.gatlingcommunity.mcp.reporting;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReportWarning(
        String severity,
        String code,
        String path,
        String message
) {
    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("severity", severity);
        values.put("code", code);
        values.put("path", path);
        values.put("message", message);
        return Map.copyOf(values);
    }
}
