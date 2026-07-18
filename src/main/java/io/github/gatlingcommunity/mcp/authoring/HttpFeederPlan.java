package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record HttpFeederPlan(
        String name,
        String type,
        String source,
        String strategy,
        List<String> columns
) {
    public HttpFeederPlan {
        name = blankDefault(name, "feeder");
        type = blankDefault(type, "csv");
        source = blankDefault(source, name + ".csv");
        strategy = blankDefault(strategy, "circular");
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", name);
        values.put("type", type);
        values.put("source", source);
        values.put("strategy", strategy);
        values.put("columns", columns);
        return values;
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
