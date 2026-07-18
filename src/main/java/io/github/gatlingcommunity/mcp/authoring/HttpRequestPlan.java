package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record HttpRequestPlan(
        String name,
        String method,
        String path,
        Map<String, String> headers,
        Map<String, String> queryParams,
        Map<String, String> formParams,
        List<MultipartPartPlan> multipartParts,
        String body,
        List<HttpResourcePlan> resources,
        AuthPlan auth,
        List<CookiePlan> cookies,
        HttpRequestOptionsPlan options,
        List<CheckPlan> checks
) {
    public HttpRequestPlan {
        method = blankDefault(method, "GET").toUpperCase();
        path = blankDefault(path, "/");
        name = blankDefault(name, method + " " + path);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        var parsedPath = ParsedPath.from(path);
        path = parsedPath.path();
        queryParams = merge(parsedPath.queryParams(), queryParams);
        formParams = formParams == null ? Map.of() : Map.copyOf(formParams);
        multipartParts = multipartParts == null ? List.of() : List.copyOf(multipartParts);
        body = body == null ? "" : body;
        resources = resources == null ? List.of() : List.copyOf(resources);
        auth = auth == null ? AuthPlan.none() : auth;
        cookies = cookies == null ? List.of() : List.copyOf(cookies);
        options = options == null ? HttpRequestOptionsPlan.defaults() : options;
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public HttpRequestPlan(String name,
                           String method,
                           String path,
                           Map<String, String> headers,
                           String body,
                           List<CheckPlan> checks) {
        this(name, method, path, headers, Map.of(), Map.of(), List.of(), body, List.of(),
                AuthPlan.none(), List.of(), HttpRequestOptionsPlan.defaults(), checks);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", name);
        values.put("method", method);
        values.put("path", path);
        values.put("headers", headers);
        values.put("queryParams", queryParams);
        values.put("formParams", formParams);
        values.put("multipartParts", multipartParts.stream().map(MultipartPartPlan::toMap).toList());
        if (!body.isBlank()) {
            values.put("body", body);
        }
        values.put("resources", resources.stream().map(HttpResourcePlan::toMap).toList());
        values.put("auth", auth.toMap());
        values.put("cookies", cookies.stream().map(CookiePlan::toMap).toList());
        values.put("options", options.toMap());
        values.put("checks", checks.stream().map(CheckPlan::toMap).toList());
        return values;
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, String> merge(Map<String, String> left, Map<String, String> right) {
        var values = new LinkedHashMap<String, String>();
        values.putAll(left);
        if (right != null) {
            values.putAll(right);
        }
        return Map.copyOf(values);
    }

    private record ParsedPath(String path, Map<String, String> queryParams) {
        private static ParsedPath from(String rawPath) {
            var separator = rawPath.indexOf('?');
            if (separator < 0) {
                return new ParsedPath(rawPath, Map.of());
            }
            var values = new LinkedHashMap<String, String>();
            var query = rawPath.substring(separator + 1);
            for (var part : query.split("&")) {
                if (part.isBlank()) {
                    continue;
                }
                var equals = part.indexOf('=');
                if (equals < 0) {
                    values.put(part, "");
                } else {
                    values.put(part.substring(0, equals), part.substring(equals + 1));
                }
            }
            var pathOnly = rawPath.substring(0, separator);
            return new ParsedPath(pathOnly.isBlank() ? "/" : pathOnly, Map.copyOf(values));
        }
    }
}
