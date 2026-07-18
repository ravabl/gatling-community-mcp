package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record AssertionPlan(
        String metric,
        String operator,
        double value
) {
    public AssertionPlan {
        metric = metric == null || metric.isBlank() ? "global.failedRequests.percent" : metric;
        operator = operator == null || operator.isBlank() ? "lt" : operator;
    }

    public static AssertionPlan defaultFailedRequests() {
        return new AssertionPlan("global.failedRequests.percent", "lt", 1.0);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("metric", metric);
        values.put("operator", operator);
        values.put("value", value);
        return values;
    }
}
