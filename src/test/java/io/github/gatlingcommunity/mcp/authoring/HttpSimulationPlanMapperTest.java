package io.github.gatlingcommunity.mcp.authoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpSimulationPlanMapperTest {
    private final HttpSimulationPlanMapper mapper = new HttpSimulationPlanMapper();

    @Test
    void roundTripsRichHttpPlanWithoutLosingBackwardCompatibleRequests() {
        var plan = mapper.fromMap(Map.of(
                "simulationClassName", "OrdersSimulation",
                "scenarioName", "Orders API",
                "baseUrl", "https://api.example.test",
                "feeders", List.of(Map.of(
                        "name", "users",
                        "type", "csv",
                        "source", "users.csv",
                        "strategy", "circular",
                        "columns", List.of("username", "password")
                )),
                "protocolOptions", Map.of(
                        "headers", Map.of("Accept", "application/json"),
                        "http2", true,
                        "proxyHost", "proxy.local",
                        "proxyPort", 8080,
                        "followRedirects", false
                ),
                "steps", List.of(
                        Map.of("type", "feed", "feederName", "users"),
                        Map.of("type", "group", "group", Map.of(
                                "name", "checkout",
                                "steps", List.of(
                                        Map.of("type", "request", "request", Map.of(
                                                "name", "POST /login",
                                                "method", "POST",
                                                "path", "/login",
                                                "headers", Map.of("X-Tenant", "acme"),
                                                "formParams", Map.of("username", "#{username}", "password", "#{password}"),
                                                "auth", Map.of("type", "basic", "username", "#{username}", "password", "#{password}"),
                                                "cookies", List.of(Map.of("name", "tenant", "value", "acme")),
                                                "options", Map.of("followRedirects", false),
                                                "checks", List.of(
                                                        Map.of("type", "status", "operator", "is", "expected", "200"),
                                                        Map.of("type", "jsonPath", "expression", "$.token", "operator", "saveAs",
                                                                "saveAs", "jwtToken")
                                                )
                                        )),
                                        Map.of("type", "pause", "pause", Map.of("durationSeconds", 2)),
                                        Map.of("type", "repeat", "loop", Map.of(
                                                "type", "repeat",
                                                "count", 3,
                                                "steps", List.of(Map.of("type", "request", "request", Map.of(
                                                        "name", "GET /api/orders",
                                                        "method", "GET",
                                                        "path", "/api/orders",
                                                        "queryParams", Map.of("state", "open"),
                                                        "headers", Map.of("Authorization", "Bearer #{jwtToken}"),
                                                        "resources", List.of(Map.of(
                                                                "name", "GET /assets/app.js",
                                                                "method", "GET",
                                                                "path", "/assets/app.js"
                                                        )),
                                                        "checks", List.of(Map.of(
                                                                "type", "status",
                                                                "operator", "is",
                                                                "expected", "200"
                                                        ))
                                                )))
                                        ))
                                )
                        )),
                        Map.of("type", "during", "loop", Map.of(
                                "type", "during",
                                "durationSeconds", 30,
                                "steps", List.of(Map.of("type", "pause", "pause", Map.of("durationSeconds", 1)))
                        )),
                        Map.of("type", "ifEquals", "conditional", Map.of(
                                "left", "#{status}",
                                "operator", "equals",
                                "right", "ready",
                                "steps", List.of(Map.of("type", "request", "request", Map.of(
                                        "name", "POST /upload",
                                        "method", "POST",
                                        "path", "/upload",
                                        "multipartParts", List.of(Map.of(
                                                "name", "file",
                                                "fileName", "orders.csv",
                                                "contentType", "text/csv",
                                                "filePath", "data/orders.csv"
                                        )),
                                        "checks", List.of(Map.of("type", "status", "expected", "201"))
                                )))
                        ))
                ),
                "requests", List.of(Map.of(
                        "name", "GET /legacy",
                        "method", "GET",
                        "path", "/legacy",
                        "checks", List.of(Map.of("type", "status", "expected", "200"))
                )),
                "injectionProfile", Map.of("type", "rampAndConstant"),
                "assertions", List.of(Map.of(
                        "metric", "global.failedRequests.percent",
                        "operator", "lt",
                        "value", 1.0
                ))
        ));

        assertThat(plan.requests()).hasSize(1);
        assertThat(plan.feeders()).hasSize(1);
        assertThat(plan.steps()).hasSize(4);
        assertThat(plan.protocolOptions().http2()).isTrue();
        assertThat(plan.protocolOptions().proxyHost()).isEqualTo("proxy.local");

        var roundTrip = plan.toMap();

        assertThat(roundTrip).containsKeys("requests", "feeders", "steps", "protocolOptions");
        assertThat(roundTrip.toString())
                .contains("users.csv", "feed", "repeat", "during", "group", "checkout", "ifEquals",
                        "queryParams", "formParams", "multipartParts", "resources", "auth", "cookies",
                        "http2", "proxy.local");
    }

    @Test
    void derivesScenarioStepsFromLegacyRequestsWhenStepsAreMissing() {
        var plan = mapper.fromMap(Map.of(
                "simulationClassName", "LegacySimulation",
                "scenarioName", "Legacy",
                "baseUrl", "https://legacy.example.test",
                "requests", List.of(Map.of(
                        "name", "GET /legacy",
                        "method", "GET",
                        "path", "/legacy",
                        "checks", List.of(Map.of("type", "status", "expected", "200"))
                ))
        ));

        assertThat(plan.requests()).hasSize(1);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().type()).isEqualTo("request");
        assertThat(plan.toMap().get("requests").toString()).contains("GET /legacy");
        assertThat(plan.toMap().get("steps").toString()).contains("GET /legacy");
    }

    @Test
    void preservesMissingAndExplicitEmptyAssertionsInsteadOfInventingDefaults() {
        var missingAssertions = mapper.fromMap(Map.of(
                "simulationClassName", "NoAssertionsSimulation",
                "steps", List.of(Map.of("type", "request", "request", Map.of(
                        "name", "GET /health",
                        "method", "GET",
                        "path", "/health"
                )))
        ));
        var explicitEmptyAssertions = mapper.fromMap(Map.of(
                "simulationClassName", "ExplicitEmptyAssertionsSimulation",
                "steps", List.of(Map.of("type", "request", "request", Map.of(
                        "name", "GET /health",
                        "method", "GET",
                        "path", "/health"
                ))),
                "assertions", List.of()
        ));

        assertThat(missingAssertions.assertions()).isEmpty();
        assertThat(explicitEmptyAssertions.assertions()).isEmpty();
    }
}
