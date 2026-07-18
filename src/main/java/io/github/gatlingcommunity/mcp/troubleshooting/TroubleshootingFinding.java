package io.github.gatlingcommunity.mcp.troubleshooting;

import java.util.LinkedHashMap;
import java.util.Map;

public record TroubleshootingFinding(
        String severity,
        String code,
        String path,
        String message,
        String evidence,
        String suggestion
) {
    public TroubleshootingFinding {
        severity = blankDefault(severity, "warning");
        code = blankDefault(code, "troubleshooting.finding");
        path = blankDefault(path, "$");
        message = blankDefault(message, code);
        evidence = evidence == null ? "" : evidence;
        suggestion = suggestion == null ? "" : suggestion;
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("severity", severity);
        values.put("code", code);
        values.put("path", path);
        values.put("message", message);
        values.put("evidence", evidence);
        values.put("suggestion", suggestion);
        return Map.copyOf(values);
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
