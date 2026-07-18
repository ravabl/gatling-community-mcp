package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record CookiePlan(String name, String value, String domain, String path) {
    public CookiePlan {
        name = name == null ? "" : name;
        value = value == null ? "" : value;
        domain = domain == null ? "" : domain;
        path = path == null || path.isBlank() ? "/" : path;
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", name);
        values.put("value", value);
        values.put("domain", domain);
        values.put("path", path);
        return values;
    }
}
