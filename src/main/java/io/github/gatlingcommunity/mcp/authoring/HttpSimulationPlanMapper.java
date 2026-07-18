package io.github.gatlingcommunity.mcp.authoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HttpSimulationPlanMapper {
    public HttpSimulationPlan fromMap(Map<String, Object> plan) {
        return new HttpSimulationPlan(
                string(plan, "simulationClassName", "ApiSimulation"),
                string(plan, "scenarioName", "HTTP API"),
                string(plan, "baseUrl", "https://example.test"),
                requests(plan.get("requests")),
                steps(plan.get("steps")),
                feeders(plan.get("feeders")),
                protocolOptions(plan.get("protocolOptions")),
                injection(plan.get("injectionProfile")),
                assertions(plan.get("assertions"))
        );
    }

    public List<Map<String, Object>> findingsToMaps(List<PlanFinding> findings) {
        return findings.stream().map(finding -> {
            var values = new LinkedHashMap<String, Object>();
            values.put("severity", finding.severity());
            values.put("code", finding.code());
            values.put("path", finding.path());
            values.put("message", finding.message());
            return (Map<String, Object>) values;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<HttpRequestPlan> requests(Object value) {
        if (!(value instanceof List<?> rawRequests)) {
            return List.of();
        }
        var requests = new ArrayList<HttpRequestPlan>();
        for (var item : rawRequests) {
            if (item instanceof Map<?, ?> rawMap) {
                var map = (Map<String, Object>) rawMap;
                requests.add(new HttpRequestPlan(
                        string(map, "name", ""),
                        string(map, "method", "GET"),
                        string(map, "path", "/"),
                        stringMap(map.get("headers")),
                        stringMap(map.get("queryParams")),
                        stringMap(map.get("formParams")),
                        multipartParts(map.get("multipartParts")),
                        string(map, "body", ""),
                        resources(map.get("resources")),
                        auth(map.get("auth")),
                        cookies(map.get("cookies")),
                        requestOptions(map.get("options")),
                        checks(map.get("checks"))
                ));
            }
        }
        return List.copyOf(requests);
    }

    @SuppressWarnings("unchecked")
    private static List<HttpScenarioStepPlan> steps(Object value) {
        if (!(value instanceof List<?> rawSteps)) {
            return List.of();
        }
        var steps = new ArrayList<HttpScenarioStepPlan>();
        for (var item : rawSteps) {
            if (item instanceof Map<?, ?> rawMap) {
                steps.add(step((Map<String, Object>) rawMap));
            }
        }
        return List.copyOf(steps);
    }

    private static HttpScenarioStepPlan step(Map<String, Object> map) {
        var type = string(map, "type", "request");
        return switch (type) {
            case "feed" -> HttpScenarioStepPlan.feed(string(map, "feederName", "feeder"));
            case "pause" -> HttpScenarioStepPlan.pause(pause(map.get("pause")));
            case "repeat", "during", "forever" -> HttpScenarioStepPlan.loop(type, loop(type, map.get("loop")));
            case "group" -> HttpScenarioStepPlan.group(group(map.get("group")));
            case "ifEquals", "doIf" -> HttpScenarioStepPlan.conditional(type, conditional(map.get("conditional")));
            default -> HttpScenarioStepPlan.request(request(map.get("request")));
        };
    }

    @SuppressWarnings("unchecked")
    private static HttpRequestPlan request(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new HttpRequestPlan("", "GET", "/", Map.of(), "", List.of());
        }
        var map = (Map<String, Object>) rawMap;
        return new HttpRequestPlan(
                string(map, "name", ""),
                string(map, "method", "GET"),
                string(map, "path", "/"),
                stringMap(map.get("headers")),
                stringMap(map.get("queryParams")),
                stringMap(map.get("formParams")),
                multipartParts(map.get("multipartParts")),
                string(map, "body", ""),
                resources(map.get("resources")),
                auth(map.get("auth")),
                cookies(map.get("cookies")),
                requestOptions(map.get("options")),
                checks(map.get("checks"))
        );
    }

    @SuppressWarnings("unchecked")
    private static List<HttpFeederPlan> feeders(Object value) {
        if (!(value instanceof List<?> rawFeeders)) {
            return List.of();
        }
        var feeders = new ArrayList<HttpFeederPlan>();
        for (var item : rawFeeders) {
            if (item instanceof Map<?, ?> rawMap) {
                var map = (Map<String, Object>) rawMap;
                feeders.add(new HttpFeederPlan(
                        string(map, "name", "feeder"),
                        string(map, "type", "csv"),
                        string(map, "source", ""),
                        string(map, "strategy", "circular"),
                        stringList(map.get("columns"))
                ));
            }
        }
        return List.copyOf(feeders);
    }

    @SuppressWarnings("unchecked")
    private static HttpProtocolOptionsPlan protocolOptions(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return HttpProtocolOptionsPlan.defaults();
        }
        var map = (Map<String, Object>) rawMap;
        return new HttpProtocolOptionsPlan(
                stringMap(map.get("headers")),
                bool(map, "followRedirects", true),
                bool(map, "http2", false),
                string(map, "proxyHost", ""),
                integer(map, "proxyPort", 0)
        );
    }

    @SuppressWarnings("unchecked")
    private static List<MultipartPartPlan> multipartParts(Object value) {
        if (!(value instanceof List<?> rawParts)) {
            return List.of();
        }
        var parts = new ArrayList<MultipartPartPlan>();
        for (var item : rawParts) {
            if (item instanceof Map<?, ?> rawMap) {
                var map = (Map<String, Object>) rawMap;
                parts.add(new MultipartPartPlan(
                        string(map, "name", ""),
                        string(map, "value", ""),
                        string(map, "fileName", ""),
                        string(map, "contentType", ""),
                        string(map, "filePath", "")
                ));
            }
        }
        return List.copyOf(parts);
    }

    @SuppressWarnings("unchecked")
    private static List<HttpResourcePlan> resources(Object value) {
        if (!(value instanceof List<?> rawResources)) {
            return List.of();
        }
        var resources = new ArrayList<HttpResourcePlan>();
        for (var item : rawResources) {
            if (item instanceof Map<?, ?> rawMap) {
                var map = (Map<String, Object>) rawMap;
                resources.add(new HttpResourcePlan(
                        string(map, "name", ""),
                        string(map, "method", "GET"),
                        string(map, "path", "/"),
                        stringMap(map.get("headers"))
                ));
            }
        }
        return List.copyOf(resources);
    }

    @SuppressWarnings("unchecked")
    private static AuthPlan auth(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return AuthPlan.none();
        }
        var map = (Map<String, Object>) rawMap;
        return new AuthPlan(
                string(map, "type", ""),
                string(map, "username", ""),
                string(map, "password", ""),
                string(map, "token", ""),
                string(map, "headerName", "Authorization")
        );
    }

    @SuppressWarnings("unchecked")
    private static List<CookiePlan> cookies(Object value) {
        if (!(value instanceof List<?> rawCookies)) {
            return List.of();
        }
        var cookies = new ArrayList<CookiePlan>();
        for (var item : rawCookies) {
            if (item instanceof Map<?, ?> rawMap) {
                var map = (Map<String, Object>) rawMap;
                cookies.add(new CookiePlan(
                        string(map, "name", ""),
                        string(map, "value", ""),
                        string(map, "domain", ""),
                        string(map, "path", "/")
                ));
            }
        }
        return List.copyOf(cookies);
    }

    @SuppressWarnings("unchecked")
    private static HttpRequestOptionsPlan requestOptions(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return HttpRequestOptionsPlan.defaults();
        }
        var map = (Map<String, Object>) rawMap;
        return new HttpRequestOptionsPlan(
                bool(map, "followRedirects", true),
                bool(map, "silent", false)
        );
    }

    @SuppressWarnings("unchecked")
    private static PausePlan pause(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new PausePlan(0);
        }
        return new PausePlan(integer((Map<String, Object>) rawMap, "durationSeconds", 0));
    }

    @SuppressWarnings("unchecked")
    private static LoopPlan loop(String type, Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LoopPlan(type, 0, 0, List.of());
        }
        var map = (Map<String, Object>) rawMap;
        return new LoopPlan(
                string(map, "type", type),
                integer(map, "count", 0),
                integer(map, "durationSeconds", 0),
                steps(map.get("steps"))
        );
    }

    @SuppressWarnings("unchecked")
    private static GroupPlan group(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new GroupPlan("group", List.of());
        }
        var map = (Map<String, Object>) rawMap;
        return new GroupPlan(string(map, "name", "group"), steps(map.get("steps")));
    }

    @SuppressWarnings("unchecked")
    private static ConditionalPlan conditional(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new ConditionalPlan("", "", "equals", "", List.of());
        }
        var map = (Map<String, Object>) rawMap;
        return new ConditionalPlan(
                string(map, "expression", ""),
                string(map, "left", ""),
                string(map, "operator", "equals"),
                string(map, "right", ""),
                steps(map.get("steps"))
        );
    }

    @SuppressWarnings("unchecked")
    private static List<CheckPlan> checks(Object value) {
        if (!(value instanceof List<?> rawChecks)) {
            return List.of();
        }
        var checks = new ArrayList<CheckPlan>();
        for (var item : rawChecks) {
            if (item instanceof Map<?, ?> rawMap) {
                var map = (Map<String, Object>) rawMap;
                checks.add(new CheckPlan(
                        string(map, "type", "status"),
                        string(map, "expression", ""),
                        string(map, "operator", "is"),
                        string(map, "expected", ""),
                        string(map, "saveAs", "")
                ));
            }
        }
        return List.copyOf(checks);
    }

    @SuppressWarnings("unchecked")
    private static InjectionProfilePlan injection(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return InjectionProfilePlan.defaultOpenModel();
        }
        var map = (Map<String, Object>) rawMap;
        return new InjectionProfilePlan(
                string(map, "type", "rampAndConstant"),
                integer(map, "rampFromUsersPerSec", 1),
                integer(map, "rampToUsersPerSec", 5),
                integer(map, "rampDurationSeconds", 30),
                integer(map, "constantUsersPerSec", 5),
                integer(map, "constantDurationSeconds", 60)
        );
    }

    @SuppressWarnings("unchecked")
    private static List<AssertionPlan> assertions(Object value) {
        if (!(value instanceof List<?> rawAssertions)) {
            return List.of();
        }
        var assertions = new ArrayList<AssertionPlan>();
        for (var item : rawAssertions) {
            if (item instanceof Map<?, ?> rawMap) {
                var map = (Map<String, Object>) rawMap;
                assertions.add(new AssertionPlan(
                        string(map, "metric", "global.failedRequests.percent"),
                        string(map, "operator", "lt"),
                        decimal(map, "value", 1.0)
                ));
            }
        }
        return List.copyOf(assertions);
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        var values = new LinkedHashMap<String, String>();
        rawMap.forEach((key, rawValue) -> values.put(String.valueOf(key), String.valueOf(rawValue)));
        return Map.copyOf(values);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream().map(String::valueOf).toList();
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        var value = map.get(key);
        if (value == null) {
            return fallback;
        }
        var text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        var value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        var value = map.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private static double decimal(Map<String, Object> map, String key, double fallback) {
        var value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text.toLowerCase(Locale.ROOT));
        }
        return fallback;
    }
}
