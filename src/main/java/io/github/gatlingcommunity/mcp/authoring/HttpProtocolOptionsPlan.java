package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.Map;

public record HttpProtocolOptionsPlan(
        Map<String, String> headers,
        boolean followRedirects,
        boolean http2,
        String proxyHost,
        int proxyPort
) {
    public HttpProtocolOptionsPlan {
        headers = headers == null ? defaultHeaders() : Map.copyOf(headers);
        proxyHost = proxyHost == null ? "" : proxyHost;
        proxyPort = Math.max(proxyPort, 0);
    }

    public static HttpProtocolOptionsPlan defaults() {
        return new HttpProtocolOptionsPlan(defaultHeaders(), true, false, "", 0);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("headers", headers);
        values.put("followRedirects", followRedirects);
        values.put("http2", http2);
        values.put("proxyHost", proxyHost);
        values.put("proxyPort", proxyPort);
        return values;
    }

    private static Map<String, String> defaultHeaders() {
        return Map.of(
                "Accept", "application/json",
                "Content-Type", "application/json"
        );
    }
}
