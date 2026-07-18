package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record HttpResourcePlan(
        String name,
        String method,
        String path,
        Map<String, String> headers
) {
    public HttpResourcePlan {
        method = method == null || method.isBlank() ? "GET" : method.toUpperCase();
        path = path == null || path.isBlank() ? "/" : path;
        name = name == null || name.isBlank() ? method + " " + path : name;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", name);
        values.put("method", method);
        values.put("path", path);
        values.put("headers", headers);
        return values;
    }
}
