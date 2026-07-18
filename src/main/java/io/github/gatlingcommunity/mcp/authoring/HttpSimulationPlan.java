package io.github.gatlingcommunity.mcp.authoring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record HttpSimulationPlan(
        String simulationClassName,
        String scenarioName,
        String baseUrl,
        List<HttpRequestPlan> requests,
        List<HttpScenarioStepPlan> steps,
        List<HttpFeederPlan> feeders,
        HttpProtocolOptionsPlan protocolOptions,
        InjectionProfilePlan injectionProfile,
        List<AssertionPlan> assertions
) {
    public HttpSimulationPlan {
        simulationClassName = blankDefault(simulationClassName, "ApiSimulation");
        scenarioName = blankDefault(scenarioName, "HTTP API");
        baseUrl = blankDefault(baseUrl, "https://example.test");
        requests = requests == null ? List.of() : List.copyOf(requests);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (steps.isEmpty() && !requests.isEmpty()) {
            steps = requests.stream().map(HttpScenarioStepPlan::request).toList();
        }
        feeders = feeders == null ? List.of() : List.copyOf(feeders);
        protocolOptions = protocolOptions == null ? HttpProtocolOptionsPlan.defaults() : protocolOptions;
        injectionProfile = injectionProfile == null ? InjectionProfilePlan.defaultOpenModel() : injectionProfile;
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
    }

    public HttpSimulationPlan(String simulationClassName,
                              String scenarioName,
                              String baseUrl,
                              List<HttpRequestPlan> requests,
                              InjectionProfilePlan injectionProfile,
                              List<AssertionPlan> assertions) {
        this(simulationClassName, scenarioName, baseUrl, requests, List.of(), List.of(),
                HttpProtocolOptionsPlan.defaults(), injectionProfile, assertions);
    }

    public Map<String, Object> toMap() {
        var values = new LinkedHashMap<String, Object>();
        values.put("simulationClassName", simulationClassName);
        values.put("scenarioName", scenarioName);
        values.put("baseUrl", baseUrl);
        values.put("requests", requests.stream().map(HttpRequestPlan::toMap).toList());
        values.put("steps", steps.stream().map(HttpScenarioStepPlan::toMap).toList());
        values.put("feeders", feeders.stream().map(HttpFeederPlan::toMap).toList());
        values.put("protocolOptions", protocolOptions.toMap());
        values.put("injectionProfile", injectionProfile.toMap());
        values.put("assertions", assertions.stream().map(AssertionPlan::toMap).toList());
        return values;
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
