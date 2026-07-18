package io.github.gatlingcommunity.mcp.troubleshooting;

import java.util.LinkedHashMap;
import java.util.Map;

public record RootCauseHypothesis(
        String code,
        String cause,
        String evidence,
        String confidence,
        String verification
) {
    public RootCauseHypothesis {
        code = blankDefault(code, "root_cause.unknown");
        cause = blankDefault(cause, "Unknown root cause");
        evidence = evidence == null ? "" : evidence;
        confidence = blankDefault(confidence, "medium");
        verification = verification == null ? "" : verification;
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("code", code);
        values.put("cause", cause);
        values.put("evidence", evidence);
        values.put("confidence", confidence);
        values.put("verification", verification);
        return Map.copyOf(values);
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
