package io.github.gatlingcommunity.mcp.authoring;

import java.util.Map;

public record HttpRequestOptionsPlan(boolean followRedirects, boolean silent) {
    public static HttpRequestOptionsPlan defaults() {
        return new HttpRequestOptionsPlan(true, false);
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "followRedirects", followRedirects,
                "silent", silent
        );
    }
}
