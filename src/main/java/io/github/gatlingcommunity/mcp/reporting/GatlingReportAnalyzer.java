package io.github.gatlingcommunity.mcp.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class GatlingReportAnalyzer {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern CONNECTION_REFUSED = Pattern.compile("connection refused", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIMEOUT = Pattern.compile("(readtimeoutexception|timeout|timed out)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TLS = Pattern.compile("(sslhandshakeexception|pkix path|certificate_unknown)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEEDER_EMPTY = Pattern.compile("feeder is now empty", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUT_OF_MEMORY = Pattern.compile("outofmemoryerror|java heap space", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNKNOWN_HOST = Pattern.compile("unknownhostexception|name or service not known", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper json = new ObjectMapper();

    public ReportAnalysisResult analyzeReportPath(Path path, ReportAnalysisOptions options) {
        var statsPath = resolveStatsPath(path);
        try {
            return analyzeReportContent(Files.readString(statsPath), options);
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to read Gatling report artifact: " + statsPath, exc);
        }
    }

    public ReportAnalysisResult analyzeLogPath(Path path, LogAnalysisOptions options) {
        try {
            return analyzeLogContent(Files.readString(path), options);
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to read Gatling log: " + path, exc);
        }
    }

    public ReportAnalysisResult analyzeReportContent(String content, ReportAnalysisOptions options) {
        var safeOptions = options == null ? ReportAnalysisOptions.defaults() : options;
        var normalized = requireContent(content, "reportContent").strip();
        var detectedSource = reportSourceType(normalized, safeOptions.contentType());
        var jsonContent = switch (detectedSource) {
            case "GATLING_STATS_JS" -> extractStatsJsObject(normalized);
            case "GATLING_STATS_JSON" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported Gatling report content type: " + detectedSource);
        };
        var root = parseJsonMap(jsonContent);
        var requestStats = new ArrayList<RequestStats>();
        collectRequestStats(root, requestStats);
        var globalStats = statsFromNode(root, "Global Information");
        var warnings = new ArrayList<ReportWarning>();
        var findings = findingsFor(globalStats, requestStats, safeOptions);
        if (globalStats.total() == 0 && requestStats.isEmpty()) {
            warnings.add(new ReportWarning("warning", "report.no.requests",
                    "$", "No Gatling request statistics were found in the report artifact."));
        }
        return new ReportAnalysisResult(detectedSource, globalStats, requestStats, findings, warnings);
    }

    public ReportAnalysisResult analyzeLogContent(String content, LogAnalysisOptions options) {
        var safeOptions = options == null ? LogAnalysisOptions.defaults() : options;
        var normalized = requireContent(content, "logText");
        var sourceType = logSourceType(normalized, safeOptions.logType());
        if ("SIMULATION_LOG".equals(sourceType)) {
            return analyzeSimulationLog(normalized, safeOptions);
        }
        return analyzeRuntimeLog(normalized);
    }

    private ReportAnalysisResult analyzeSimulationLog(String content, LogAnalysisOptions options) {
        var aggregates = new LinkedHashMap<String, MutableRequestStats>();
        var warnings = new ArrayList<ReportWarning>();
        warnings.add(new ReportWarning("warning", "log.simulationlog.unstable",
                "$", "Gatling simulation.log is an undocumented implementation detail; this parser is best-effort."));

        for (var line : content.lines().toList()) {
            parseSimulationLogRequest(line).ifPresent(event -> aggregates
                    .computeIfAbsent(event.name(), MutableRequestStats::new)
                    .record(event.durationMs(), event.ok()));
        }

        var requestStats = aggregates.values().stream()
                .map(MutableRequestStats::toRequestStats)
                .toList();
        var globalStats = MutableRequestStats.global(aggregates.values().stream().toList()).toRequestStats();
        var findings = findingsFor(globalStats, requestStats, new ReportAnalysisOptions(
                "AUTO",
                options.errorRateThreshold(),
                options.p95ThresholdMs(),
                options.p99ThresholdMs()
        ));
        if (globalStats.total() == 0) {
            warnings.add(new ReportWarning("warning", "log.no.request.events",
                    "$", "No REQUEST events were found in the supplied simulation log."));
        }
        return new ReportAnalysisResult("SIMULATION_LOG", globalStats, requestStats, findings, warnings);
    }

    private ReportAnalysisResult analyzeRuntimeLog(String content) {
        var findings = new ArrayList<ReportFinding>();
        var warnings = new ArrayList<ReportWarning>();
        var lines = content.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var path = "$.lines[%d]".formatted(i + 1);
            if (CONNECTION_REFUSED.matcher(line).find()) {
                findings.add(new ReportFinding("error", "log.connection.refused", path,
                        "Target endpoint refused the TCP connection. Check base URL, port, routing, and service readiness."));
            }
            if (TIMEOUT.matcher(line).find()) {
                findings.add(new ReportFinding("error", "log.timeout", path,
                        "A timeout was observed. Check request timeouts, server latency, saturation, and network path."));
            }
            if (TLS.matcher(line).find()) {
                findings.add(new ReportFinding("error", "log.tls.handshake", path,
                        "TLS handshake failed. Check CA trust, certificate chain, hostname, and proxy interception."));
            }
            if (FEEDER_EMPTY.matcher(line).find()) {
                findings.add(new ReportFinding("warning", "log.feeder.empty", path,
                        "A feeder was exhausted. Use enough data, circular/random feeders, or lower the user volume."));
            }
            if (OUT_OF_MEMORY.matcher(line).find()) {
                findings.add(new ReportFinding("error", "log.jvm.oom", path,
                        "The JVM ran out of memory. Check heap sizing, response body retention, feeders, and report volume."));
            }
            if (UNKNOWN_HOST.matcher(line).find()) {
                findings.add(new ReportFinding("error", "log.dns.unknown-host", path,
                        "DNS resolution failed. Check hostnames, resolver configuration, VPN, and container DNS."));
            }
        }
        if (findings.isEmpty()) {
            warnings.add(new ReportWarning("info", "log.no.known-patterns",
                    "$", "No known Gatling runtime failure patterns were detected."));
        }
        return new ReportAnalysisResult("RUNTIME_LOG", RequestStats.empty("Global Information"),
                List.of(), findings, warnings);
    }

    private Path resolveStatsPath(Path path) {
        if (Files.isRegularFile(path)) {
            return path;
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Report path is neither a file nor a directory: " + path);
        }
        for (var candidate : List.of(path.resolve("js/stats.js"), path.resolve("js/stats.json"), path.resolve("stats.json"))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        try (var files = Files.walk(path, 4)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> {
                        var name = file.getFileName().toString();
                        return "stats.js".equals(name) || "stats.json".equals(name);
                    })
                    .min(Comparator.comparing(Path::toString))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No Gatling stats.js or stats.json file found under: " + path));
        } catch (IOException exc) {
            throw new UncheckedIOException("Failed to scan Gatling report directory: " + path, exc);
        }
    }

    private String reportSourceType(String content, String requestedType) {
        return switch (requestedType) {
            case "STATS_JS", "GATLING_STATS_JS" -> "GATLING_STATS_JS";
            case "STATS_JSON", "GATLING_STATS_JSON" -> "GATLING_STATS_JSON";
            case "AUTO" -> content.startsWith("{") ? "GATLING_STATS_JSON" : "GATLING_STATS_JS";
            default -> throw new IllegalArgumentException("Unsupported report contentType: " + requestedType);
        };
    }

    private String logSourceType(String content, String requestedType) {
        return switch (requestedType) {
            case "SIMULATION_LOG" -> "SIMULATION_LOG";
            case "RUNTIME_LOG" -> "RUNTIME_LOG";
            case "AUTO" -> content.lines().anyMatch(line -> line.startsWith("REQUEST\t"))
                    ? "SIMULATION_LOG"
                    : "RUNTIME_LOG";
            default -> throw new IllegalArgumentException("Unsupported logType: " + requestedType);
        };
    }

    private String extractStatsJsObject(String content) {
        var first = content.indexOf('{');
        var last = content.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("Gatling stats.js does not contain a JSON object.");
        }
        return content.substring(first, last + 1);
    }

    private Map<String, Object> parseJsonMap(String content) {
        try {
            return json.readValue(content, MAP_TYPE);
        } catch (JsonProcessingException exc) {
            throw new IllegalArgumentException("Failed to parse Gatling report JSON: " + exc.getOriginalMessage(), exc);
        }
    }

    private void collectRequestStats(Map<String, Object> node, List<RequestStats> requestStats) {
        if ("REQUEST".equalsIgnoreCase(text(node.get("type"), ""))) {
            requestStats.add(statsFromNode(node, text(node.get("name"), "request")));
        }
        for (var child : map(node.get("contents")).values()) {
            collectRequestStats(map(child), requestStats);
        }
    }

    private RequestStats statsFromNode(Map<String, Object> node, String fallbackName) {
        var stats = map(node.get("stats"));
        var counts = map(stats.get("numberOfRequests"));
        var total = intCell(counts, "total");
        var ok = intCell(counts, "ok");
        var ko = intCell(counts, "ko");
        var errorRate = total == 0 ? 0.0 : (ko * 100.0) / total;
        return new RequestStats(
                text(node.get("name"), fallbackName),
                total,
                ok,
                ko,
                errorRate,
                longCell(map(stats.get("minResponseTime")), "total"),
                longCell(map(stats.get("maxResponseTime")), "total"),
                longCell(map(stats.get("meanResponseTime")), "total"),
                longCell(map(stats.get("percentiles1")), "total"),
                longCell(map(stats.get("percentiles2")), "total"),
                longCell(map(stats.get("percentiles3")), "total"),
                longCell(map(stats.get("percentiles4")), "total"),
                doubleCell(map(stats.get("meanNumberOfRequestsPerSecond")), "total")
        );
    }

    private List<ReportFinding> findingsFor(RequestStats globalStats,
                                            List<RequestStats> requestStats,
                                            ReportAnalysisOptions options) {
        var findings = new ArrayList<ReportFinding>();
        addThresholdFindings(findings, "$.globalStats", globalStats, options);
        for (int i = 0; i < requestStats.size(); i++) {
            var stats = requestStats.get(i);
            var path = "$.requestStats[%d]".formatted(i);
            addThresholdFindings(findings, path, stats, options);
            if (stats.ko() > 0) {
                findings.add(new ReportFinding("error", "report.request.failures", path,
                        "Request '%s' has %d KO responses out of %d."
                                .formatted(stats.name(), stats.ko(), stats.total())));
            }
        }
        return findings;
    }

    private void addThresholdFindings(List<ReportFinding> findings,
                                      String path,
                                      RequestStats stats,
                                      ReportAnalysisOptions options) {
        if (stats.total() > 0 && stats.errorRate() > options.errorRateThreshold()) {
            findings.add(new ReportFinding("error", "report.error-rate.high", path,
                    "'%s' error rate %.2f%% is above threshold %.2f%%."
                            .formatted(stats.name(), stats.errorRate(), options.errorRateThreshold())));
        }
        if (stats.p95Ms() > options.p95ThresholdMs()) {
            findings.add(new ReportFinding("warning", "report.p95.high", path,
                    "'%s' p95 %d ms is above threshold %d ms."
                            .formatted(stats.name(), stats.p95Ms(), options.p95ThresholdMs())));
        }
        if (stats.p99Ms() > options.p99ThresholdMs()) {
            findings.add(new ReportFinding("warning", "report.p99.high", path,
                    "'%s' p99 %d ms is above threshold %d ms."
                            .formatted(stats.name(), stats.p99Ms(), options.p99ThresholdMs())));
        }
    }

    private Optional<RequestEvent> parseSimulationLogRequest(String line) {
        if (!line.startsWith("REQUEST")) {
            return Optional.empty();
        }
        var tokens = java.util.Arrays.stream(line.split("\t"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
        var statusIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if ("OK".equals(tokens.get(i)) || "KO".equals(tokens.get(i))) {
                statusIndex = i;
                break;
            }
        }
        if (statusIndex < 4) {
            return Optional.empty();
        }
        var name = tokens.get(statusIndex - 3);
        var startMs = parseLong(tokens.get(statusIndex - 2));
        var endMs = parseLong(tokens.get(statusIndex - 1));
        if (startMs.isEmpty() || endMs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RequestEvent(
                name,
                "OK".equals(tokens.get(statusIndex)),
                Math.max(0, endMs.orElseThrow() - startMs.orElseThrow())
        ));
    }

    private static String requireContent(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing content argument: " + name);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private static String text(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private static int intCell(Map<String, Object> cell, String key) {
        return parseLong(text(cell.get(key), "0")).map(Long::intValue).orElse(0);
    }

    private static long longCell(Map<String, Object> cell, String key) {
        return parseLong(text(cell.get(key), "0")).orElse(0L);
    }

    private static double doubleCell(Map<String, Object> cell, String key) {
        var value = text(cell.get(key), "0")
                .replace(",", "")
                .replace(" ms", "")
                .trim();
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exc) {
            return 0.0;
        }
    }

    private static Optional<Long> parseLong(String value) {
        var normalized = value == null ? "" : value
                .replace(",", "")
                .replace(" ms", "")
                .trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Math.round(Double.parseDouble(normalized)));
        } catch (NumberFormatException exc) {
            return Optional.empty();
        }
    }

    private record RequestEvent(String name, boolean ok, long durationMs) {
    }

    private static final class MutableRequestStats {
        private final String name;
        private final List<Long> durations = new ArrayList<>();
        private int ok;
        private int ko;

        private MutableRequestStats(String name) {
            this.name = name;
        }

        private static MutableRequestStats global(List<MutableRequestStats> values) {
            var global = new MutableRequestStats("Global Information");
            for (var value : values) {
                global.durations.addAll(value.durations);
                global.ok += value.ok;
                global.ko += value.ko;
            }
            return global;
        }

        private void record(long durationMs, boolean success) {
            durations.add(durationMs);
            if (success) {
                ok++;
            } else {
                ko++;
            }
        }

        private RequestStats toRequestStats() {
            var sorted = durations.stream().sorted().toList();
            var total = ok + ko;
            var mean = sorted.isEmpty()
                    ? 0
                    : Math.round(sorted.stream().mapToLong(Long::longValue).average().orElse(0.0));
            var errorRate = total == 0 ? 0.0 : (ko * 100.0) / total;
            return new RequestStats(
                    name,
                    total,
                    ok,
                    ko,
                    errorRate,
                    sorted.isEmpty() ? 0 : sorted.getFirst(),
                    sorted.isEmpty() ? 0 : sorted.getLast(),
                    mean,
                    percentile(sorted, 50),
                    percentile(sorted, 75),
                    percentile(sorted, 95),
                    percentile(sorted, 99),
                    0.0
            );
        }

        private static long percentile(List<Long> values, int percentile) {
            if (values.isEmpty()) {
                return 0;
            }
            var index = (int) Math.ceil((percentile / 100.0) * values.size()) - 1;
            return values.get(Math.clamp(index, 0, values.size() - 1));
        }
    }
}
