package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record CheckPlan(
        String type,
        String expression,
        String operator,
        String expected,
        String saveAs
) {
    public CheckPlan {
        type = blankDefault(type, "status");
        expression = expression == null ? "" : expression;
        operator = blankDefault(operator, "is");
        expected = expected == null ? "" : expected;
        saveAs = saveAs == null ? "" : saveAs;
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("type", type);
        if (!expression.isBlank()) {
            values.put("expression", expression);
        }
        values.put("operator", operator);
        if (!expected.isBlank()) {
            values.put("expected", expected);
        }
        if (!saveAs.isBlank()) {
            values.put("saveAs", saveAs);
        }
        return values;
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
