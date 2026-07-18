package io.github.gatlingcommunity.mcp.importing;

import java.util.LinkedHashMap;
import java.util.Map;

public record ImportWarning(
        String severity,
        String code,
        String path,
        String message
) {
    public ImportWarning {
        severity = blankDefault(severity, "warning");
        code = blankDefault(code, "import.warning");
        path = path == null ? "" : path;
        message = blankDefault(message, code);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("severity", severity);
        values.put("code", code);
        values.put("path", path);
        values.put("message", message);
        return values;
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
