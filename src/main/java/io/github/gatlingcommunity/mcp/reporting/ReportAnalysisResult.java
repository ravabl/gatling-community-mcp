package io.github.gatlingcommunity.mcp.reporting;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ReportAnalysisResult(
        String sourceType,
        RequestStats globalStats,
        List<RequestStats> requestStats,
        List<ReportFinding> findings,
        List<ReportWarning> warnings
) {
    public ReportAnalysisResult {
        sourceType = sourceType == null || sourceType.isBlank() ? "UNKNOWN" : sourceType;
        globalStats = globalStats == null ? RequestStats.empty("Global Information") : globalStats;
        requestStats = requestStats == null ? List.of() : List.copyOf(requestStats);
        findings = findings == null ? List.of() : List.copyOf(findings);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("sourceType", sourceType);
        values.put("requestCount", requestStats.size());
        values.put("globalStats", globalStats.toMap());
        values.put("requestStats", requestStats.stream().map(RequestStats::toMap).toList());
        values.put("topSlowRequests", requestStats.stream()
                .sorted(Comparator.comparingLong(RequestStats::p95Ms).reversed())
                .limit(5)
                .map(RequestStats::toMap)
                .toList());
        values.put("topFailedRequests", requestStats.stream()
                .filter(stats -> stats.ko() > 0)
                .sorted(Comparator.comparingInt(RequestStats::ko).reversed())
                .limit(5)
                .map(RequestStats::toMap)
                .toList());
        values.put("findings", findings.stream().map(ReportFinding::toMap).toList());
        values.put("warnings", warnings.stream().map(ReportWarning::toMap).toList());
        return Map.copyOf(values);
    }
}
