package io.github.gatlingcommunity.mcp.reporting;

public record LogAnalysisOptions(
        String logType,
        double errorRateThreshold,
        long p95ThresholdMs,
        long p99ThresholdMs
) {
    public LogAnalysisOptions {
        logType = normalize(logType);
        errorRateThreshold = errorRateThreshold <= 0 ? 1.0 : errorRateThreshold;
        p95ThresholdMs = p95ThresholdMs <= 0 ? 1_000 : p95ThresholdMs;
        p99ThresholdMs = p99ThresholdMs <= 0 ? 2_000 : p99ThresholdMs;
    }

    public static LogAnalysisOptions defaults() {
        return new LogAnalysisOptions("AUTO", 1.0, 1_000, 2_000);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "AUTO";
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
