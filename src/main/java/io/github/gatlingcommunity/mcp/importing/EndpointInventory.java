package io.github.gatlingcommunity.mcp.importing;

import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record EndpointInventory(
        List<Endpoint> endpoints,
        Map<String, List<String>> groups,
        List<String> authSchemes,
        List<String> warnings
) {
    public EndpointInventory {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        groups = copyGroups(groups);
        authSchemes = authSchemes == null ? List.of() : List.copyOf(new LinkedHashSet<>(authSchemes));
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static EndpointInventory fromRequests(List<HttpRequestPlan> requests) {
        var endpoints = new ArrayList<Endpoint>();
        for (var request : requests == null ? List.<HttpRequestPlan>of() : requests) {
            endpoints.add(Endpoint.fromRequest(request, inferTags(request.path()), ""));
        }
        return fromEndpoints(endpoints, List.of(), List.of());
    }

    public static EndpointInventory fromEndpoints(List<Endpoint> endpoints,
                                                  List<String> authSchemes,
                                                  List<String> warnings) {
        return new EndpointInventory(endpoints, groupsFor(endpoints), authSchemes, warnings);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("endpoints", endpoints.stream().map(Endpoint::toMap).toList());
        values.put("groups", groups);
        values.put("authSchemes", authSchemes);
        values.put("warnings", warnings);
        return Map.copyOf(values);
    }

    private static Map<String, List<String>> groupsFor(List<Endpoint> endpoints) {
        var groups = new LinkedHashMap<String, List<String>>();
        for (var endpoint : endpoints == null ? List.<Endpoint>of() : endpoints) {
            var labels = endpoint.tags().isEmpty() ? inferTags(endpoint.path()) : endpoint.tags();
            for (var label : labels) {
                var group = normalizeGroup(label);
                if (!group.isBlank()) {
                    groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(endpoint.key());
                }
            }
        }
        return groups;
    }

    private static List<String> inferTags(String path) {
        var value = path == null || path.isBlank() ? "/" : path;
        var normalized = value.startsWith("/") ? value.substring(1) : value;
        var first = normalized.split("[/?]", 2)[0];
        if (first.isBlank()) {
            return List.of("default");
        }
        return List.of(first);
    }

    private static String normalizeGroup(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }

    private static Map<String, List<String>> copyGroups(Map<String, List<String>> groups) {
        if (groups == null || groups.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, List<String>>();
        groups.forEach((key, value) -> copy.put(key, value == null ? List.of() : List.copyOf(value)));
        return Map.copyOf(copy);
    }

    public record Endpoint(
            String name,
            String method,
            String path,
            List<String> tags,
            String sourcePath,
            boolean hasRequestBody,
            List<String> headerNames
    ) {
        public Endpoint {
            name = blankDefault(name, method + " " + path);
            method = blankDefault(method, "GET").toUpperCase(Locale.ROOT);
            path = blankDefault(path, "/");
            tags = tags == null ? List.of() : List.copyOf(tags.stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(String::trim)
                    .toList());
            sourcePath = sourcePath == null ? "" : sourcePath;
            headerNames = headerNames == null ? List.of() : List.copyOf(headerNames);
        }

        public static Endpoint fromRequest(HttpRequestPlan request, List<String> tags, String sourcePath) {
            return new Endpoint(
                    request.name(),
                    request.method(),
                    request.path(),
                    tags,
                    sourcePath,
                    !request.body().isBlank(),
                    request.headers().keySet().stream().sorted().toList()
            );
        }

        public String key() {
            return "%s %s".formatted(method, path);
        }

        public Map<String, Object> toMap() {
            var values = new LinkedHashMap<String, Object>();
            values.put("name", name);
            values.put("method", method);
            values.put("path", path);
            values.put("key", key());
            values.put("tags", tags);
            values.put("sourcePath", sourcePath);
            values.put("hasRequestBody", hasRequestBody);
            values.put("headerNames", headerNames);
            return Map.copyOf(values);
        }

        private static String blankDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
