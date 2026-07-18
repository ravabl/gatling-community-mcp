package io.github.gatlingcommunity.mcp.reporting;

import java.util.LinkedHashMap;
import java.util.Map;

public record RequestStats(
        String name,
        int total,
        int ok,
        int ko,
        double errorRate,
        long minMs,
        long maxMs,
        long meanMs,
        long p50Ms,
        long p75Ms,
        long p95Ms,
        long p99Ms,
        double meanRps
) {
    public RequestStats {
        name = name == null || name.isBlank() ? "Global Information" : name;
        errorRate = round2(errorRate);
        meanRps = round2(meanRps);
    }

    public static RequestStats empty(String name) {
        return new RequestStats(name, 0, 0, 0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0.0);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", name);
        values.put("total", total);
        values.put("ok", ok);
        values.put("ko", ko);
        values.put("errorRate", errorRate);
        values.put("minMs", minMs);
        values.put("maxMs", maxMs);
        values.put("meanMs", meanMs);
        values.put("p50Ms", p50Ms);
        values.put("p75Ms", p75Ms);
        values.put("p95Ms", p95Ms);
        values.put("p99Ms", p99Ms);
        values.put("meanRps", meanRps);
        return Map.copyOf(values);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
