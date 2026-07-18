package io.github.gatlingcommunity.mcp.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class HttpSimulationPlanner {
    private static final Pattern FEEDER_PATTERN = Pattern.compile("\\bfeed\\s+([A-Za-z0-9_.\\-/]+)");
    private static final Pattern PAUSE_PATTERN = Pattern.compile("\\bpause\\s+(\\d+)\\s*(second|seconds|sec|s)?");
    private static final Pattern REPEAT_PATTERN = Pattern.compile("\\brepeat\\s+(\\d+)");
    private static final Pattern PROXY_PATTERN = Pattern.compile("\\bproxy\\s+([A-Za-z0-9_.-]+)(?::(\\d+))?");

    public HttpSimulationPlan plan(String simulationClassName, String goal, String baseUrl) {
        var normalizedGoal = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        if (normalizedGoal.contains("login") || normalizedGoal.contains("token") || normalizedGoal.contains("jwt")) {
            return loginAndBearerApiPlan(simulationClassName, baseUrl, normalizedGoal);
        }
        return defaultApiPlan(simulationClassName, baseUrl, normalizedGoal);
    }

    public List<Map<String, Object>> planCorrelation(String goal) {
        var plan = plan("ApiSimulation", goal, "https://example.test");
        return plan.requests().stream()
                .flatMap(request -> request.checks().stream())
                .filter(check -> !check.saveAs().isBlank())
                .map(CheckPlan::toMap)
                .toList();
    }

    public List<Map<String, Object>> planChecks(String goal) {
        var plan = plan("ApiSimulation", goal, "https://example.test");
        return plan.requests().stream()
                .flatMap(request -> request.checks().stream())
                .map(CheckPlan::toMap)
                .toList();
    }

    public Map<String, Object> planInjection() {
        return InjectionProfilePlan.defaultOpenModel().toMap();
    }

    public List<Map<String, Object>> planAssertions() {
        return List.of(AssertionPlan.defaultFailedRequests().toMap());
    }

    private static HttpSimulationPlan loginAndBearerApiPlan(String simulationClassName,
                                                            String baseUrl,
                                                            String normalizedGoal) {
        var login = new HttpRequestPlan(
                "POST /login",
                "POST",
                "/login",
                Map.of(),
                Map.of(),
                containsAny(normalizedGoal, "form", "basic auth")
                        ? Map.of("username", "#{username}", "password", "#{password}")
                        : Map.of(),
                List.of(),
                containsAny(normalizedGoal, "form", "basic auth")
                        ? ""
                        : "{\"username\":\"#{username}\",\"password\":\"#{password}\"}",
                List.of(),
                normalizedGoal.contains("basic auth")
                        ? new AuthPlan("basic", "#{username}", "#{password}", "", "Authorization")
                        : AuthPlan.none(),
                List.of(),
                HttpRequestOptionsPlan.defaults(),
                List.of(
                        new CheckPlan("status", "", "is", "200", ""),
                        new CheckPlan("jsonPath", "$.token", "saveAs", "", "jwtToken")
                )
        );
        var orders = new HttpRequestPlan(
                "GET /api/orders",
                "GET",
                "/api/orders",
                Map.of("Authorization", "Bearer #{jwtToken}"),
                normalizedGoal.contains("open") ? Map.of("state", "open") : Map.of(),
                Map.of(),
                List.of(),
                "",
                List.of(),
                AuthPlan.none(),
                List.of(),
                HttpRequestOptionsPlan.defaults(),
                List.of(
                        new CheckPlan("status", "", "is", "200", ""),
                        new CheckPlan("jsonPath", "$.orders[0]", "exists", "", "")
                )
        );
        var requests = List.of(login, orders);
        return new HttpSimulationPlan(
                simulationClassName,
                "Authenticated orders API",
                baseUrl,
                requests,
                richSteps(normalizedGoal, login, orders),
                feeders(normalizedGoal),
                protocolOptions(normalizedGoal),
                InjectionProfilePlan.defaultOpenModel(),
                List.of(AssertionPlan.defaultFailedRequests())
        );
    }

    private static HttpSimulationPlan defaultApiPlan(String simulationClassName, String baseUrl, String normalizedGoal) {
        var request = new HttpRequestPlan(
                "GET /resource",
                "GET",
                "/resource",
                Map.of(),
                "",
                List.of(
                        new CheckPlan("status", "", "is", "200", ""),
                        new CheckPlan("jsonPath", "$.id", "exists", "", "")
                )
        );
        return new HttpSimulationPlan(
                simulationClassName,
                "HTTP API",
                baseUrl,
                List.of(request),
                richSteps(normalizedGoal, request),
                feeders(normalizedGoal),
                protocolOptions(normalizedGoal),
                InjectionProfilePlan.defaultOpenModel(),
                List.of(AssertionPlan.defaultFailedRequests())
        );
    }

    private static List<HttpFeederPlan> feeders(String normalizedGoal) {
        var matcher = FEEDER_PATTERN.matcher(normalizedGoal);
        if (!matcher.find()) {
            return List.of();
        }
        var source = matcher.group(1);
        var name = source.contains(".") ? source.substring(0, source.indexOf('.')) : source;
        return List.of(new HttpFeederPlan(name, source.endsWith(".json") ? "json" : "csv",
                source, "circular", List.of("username", "password")));
    }

    private static HttpProtocolOptionsPlan protocolOptions(String normalizedGoal) {
        var proxyMatcher = PROXY_PATTERN.matcher(normalizedGoal);
        var proxyHost = "";
        var proxyPort = 0;
        if (proxyMatcher.find()) {
            proxyHost = proxyMatcher.group(1);
            proxyPort = proxyMatcher.group(2) == null ? 8080 : Integer.parseInt(proxyMatcher.group(2));
        }
        return new HttpProtocolOptionsPlan(
                Map.of("Accept", "application/json", "Content-Type", "application/json"),
                !normalizedGoal.contains("no redirect"),
                normalizedGoal.contains("http2") || normalizedGoal.contains("http/2"),
                proxyHost,
                proxyPort
        );
    }

    private static List<HttpScenarioStepPlan> richSteps(String normalizedGoal, HttpRequestPlan... requests) {
        var steps = new ArrayList<HttpScenarioStepPlan>();
        var feeders = feeders(normalizedGoal);
        if (!feeders.isEmpty()) {
            steps.add(HttpScenarioStepPlan.feed(feeders.getFirst().name()));
        }
        var requestSteps = new ArrayList<HttpScenarioStepPlan>();
        for (var request : requests) {
            requestSteps.add(HttpScenarioStepPlan.request(request));
            pause(normalizedGoal).ifPresent(pause -> requestSteps.add(HttpScenarioStepPlan.pause(pause)));
        }
        repeat(normalizedGoal, requests.length > 1 ? requests[requests.length - 1] : requests[0])
                .ifPresent(requestSteps::add);
        if (normalizedGoal.contains("if token exists")) {
            requestSteps.add(HttpScenarioStepPlan.conditional("ifEquals", new ConditionalPlan(
                    "",
                    "#{jwtToken}",
                    "exists",
                    "",
                    List.of(HttpScenarioStepPlan.request(new HttpRequestPlan(
                            "POST /upload",
                            "POST",
                            "/upload",
                            Map.of("Authorization", "Bearer #{jwtToken}"),
                            Map.of(),
                            Map.of(),
                            List.of(new MultipartPartPlan("file", "", "orders.csv", "text/csv", "data/orders.csv")),
                            "",
                            List.of(),
                            AuthPlan.none(),
                            List.of(),
                            HttpRequestOptionsPlan.defaults(),
                            List.of(new CheckPlan("status", "", "is", "201", ""))
                    )))
            )));
        }
        if (normalizedGoal.contains("group checkout")) {
            steps.add(HttpScenarioStepPlan.group(new GroupPlan("checkout", requestSteps)));
        } else {
            steps.addAll(requestSteps);
        }
        return List.copyOf(steps);
    }

    private static java.util.Optional<PausePlan> pause(String normalizedGoal) {
        var matcher = PAUSE_PATTERN.matcher(normalizedGoal);
        if (matcher.find()) {
            return java.util.Optional.of(new PausePlan(Integer.parseInt(matcher.group(1))));
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<HttpScenarioStepPlan> repeat(String normalizedGoal, HttpRequestPlan request) {
        var matcher = REPEAT_PATTERN.matcher(normalizedGoal);
        if (matcher.find()) {
            return java.util.Optional.of(HttpScenarioStepPlan.loop("repeat", new LoopPlan(
                    "repeat",
                    Integer.parseInt(matcher.group(1)),
                    0,
                    List.of(HttpScenarioStepPlan.request(request))
            )));
        }
        return java.util.Optional.empty();
    }

    private static boolean containsAny(String value, String... needles) {
        for (var needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
