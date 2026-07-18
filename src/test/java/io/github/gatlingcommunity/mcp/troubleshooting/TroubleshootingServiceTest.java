package io.github.gatlingcommunity.mcp.troubleshooting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gatlingcommunity.mcp.authoring.AssertionPlan;
import io.github.gatlingcommunity.mcp.authoring.CheckPlan;
import io.github.gatlingcommunity.mcp.authoring.GroupPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpFeederPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpRequestPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpScenarioStepPlan;
import io.github.gatlingcommunity.mcp.authoring.HttpSimulationPlan;
import io.github.gatlingcommunity.mcp.authoring.InjectionProfilePlan;
import io.github.gatlingcommunity.mcp.authoring.LoopPlan;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TroubleshootingServiceTest {
    private final TroubleshootingService service = new TroubleshootingService();

    @Test
    void explainsRuntimeErrorsAsRootCauseHypotheses() {
        var result = service.explainErrors("""
                ERROR io.netty.handler.timeout.ReadTimeoutException while calling GET /api/orders
                WARN status.find.is(200), but actually found 500
                WARN Feeder is now empty, stopping engine
                """);

        assertThat(result.findings())
                .extracting(TroubleshootingFinding::code)
                .contains("log.timeout", "log.server.error", "log.feeder.empty", "log.check.failed");
        assertThat(result.rootCauseHypotheses())
                .extracting(RootCauseHypothesis::code)
                .contains("root_cause.timeout", "root_cause.server.error", "root_cause.feeder.empty");
        assertThat(result.verificationSteps()).anySatisfy(step ->
                assertThat(step).contains("application logs"));
        assertThat(result.toMap().toString()).contains("verificationSteps", "nextActions", "confidence");
    }

    @Test
    void explainsNoKnownPatternsWithLowConfidenceAndNoRootCauseHypotheses() {
        var result = service.explainErrors("all requests completed successfully");

        assertThat(result.confidence()).isEqualTo("low");
        assertThat(result.rootCauseHypotheses()).isEmpty();
        assertThat(result.findings())
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity()).isEqualTo("info");
                    assertThat(finding.code()).isEqualTo("errors.no_known_patterns");
                });
        assertThat(result.risks()).isEmpty();
    }

    @Test
    void suggestsLoadModelFromTargetRpsAndP95() {
        var result = service.suggestLoadModel(50.0, 0, 300, 60, 250, "open");

        assertThat(result.suggestedModel())
                .containsEntry("workloadType", "open")
                .containsEntry("estimatedConcurrentUsers", 13)
                .containsEntry("targetRps", 50.0);
        assertThat(result.injectionProfile())
                .containsEntry("type", "rampAndConstant")
                .containsEntry("constantUsersPerSec", 50)
                .containsEntry("constantDurationSeconds", 240);
        assertThat(result.rootCauseHypotheses().getFirst().evidence())
                .contains("targetRps=50.00", "p95Ms=250", "modelUsers=13");
    }

    @Test
    void detectsFeederExhaustionAndUndefinedFeeders() {
        var plan = new HttpSimulationPlan(
                "FeederRiskSimulation",
                "Feeder risk",
                "https://api.example.test",
                List.of(requestWithTokenReference()),
                List.of(HttpScenarioStepPlan.feed("users"),
                        HttpScenarioStepPlan.loop("repeat", new LoopPlan("repeat", 5, 0, List.of(
                                HttpScenarioStepPlan.feed("missing")
                        )))),
                List.of(new HttpFeederPlan("users", "csv", "users.csv", "queue", List.of("username", "password"))),
                null,
                new InjectionProfilePlan("rampAndConstant", 1, 10, 30, 10, 60),
                List.of(AssertionPlan.defaultFailedRequests())
        );

        var result = service.checkFeederRisk(plan, "username,password\nu1,p1\nu2,p2\n", 20, 60);

        assertThat(result.findings())
                .extracting(TroubleshootingFinding::code)
                .contains("feeder.undefined", "feeder.finite_strategy", "feeder.rows_insufficient");
        assertThat(result.rootCauseHypotheses().getFirst().verification())
                .contains("count feed calls");
    }

    @Test
    void detectsMissingCorrelationBeforeSessionVariableUse() {
        var plan = new HttpSimulationPlan(
                "CorrelationRiskSimulation",
                "Correlation risk",
                "https://api.example.test",
                List.of(requestWithTokenReference()),
                null,
                null,
                null,
                InjectionProfilePlan.defaultOpenModel(),
                List.of(AssertionPlan.defaultFailedRequests())
        );

        var result = service.checkCorrelationRisk(plan,
                "scenario(\"Orders\").exec(http(\"orders\").get(\"/api/orders\").header(\"Authorization\", \"Bearer #{jwtToken}\"))");

        assertThat(result.findings())
                .extracting(TroubleshootingFinding::code)
                .contains("correlation.reference_without_save", "correlation.auth_without_token_capture");
        assertThat(result.findings().toString()).contains("jwtToken", "saveAs");
    }

    @Test
    void acceptsCorrelationWhenValueIsSavedBeforeUse() {
        var login = new HttpRequestPlan(
                "POST /login",
                "POST",
                "/login",
                Map.of(),
                "{\"username\":\"#{username}\"}",
                List.of(new CheckPlan("jsonPath", "$.token", "exists", "", "jwtToken"))
        );
        var orders = requestWithTokenReference();
        var plan = new HttpSimulationPlan(
                "CorrelationOkSimulation",
                "Correlation ok",
                "https://api.example.test",
                List.of(login, orders),
                null,
                null,
                null,
                InjectionProfilePlan.defaultOpenModel(),
                List.of(AssertionPlan.defaultFailedRequests())
        );

        var result = service.checkCorrelationRisk(plan, "");

        assertThat(result.findings())
                .extracting(TroubleshootingFinding::code)
                .doesNotContain("correlation.reference_without_save");
    }

    @Test
    void handlesNestedFeederAndCorrelationEdgesFromStepExecutionOrder() {
        var login = new HttpRequestPlan(
                "POST /login",
                "POST",
                "/login",
                Map.of(),
                "{\"username\":\"#{username}\"}",
                List.of(new CheckPlan("jsonPath", "$.token", "saveAs", "", "jwtToken"))
        );
        var orders = requestWithTokenReference();
        var plan = new HttpSimulationPlan(
                "NestedTroubleshootingSimulation",
                "Nested troubleshooting",
                "https://api.example.test",
                List.of(),
                List.of(
                        HttpScenarioStepPlan.feed("users"),
                        HttpScenarioStepPlan.group(new GroupPlan("checkout", List.of(
                                HttpScenarioStepPlan.request(login),
                                HttpScenarioStepPlan.loop("repeat", new LoopPlan("repeat", 2, 0, List.of(
                                        HttpScenarioStepPlan.feed("missing"),
                                        HttpScenarioStepPlan.request(orders)
                                )))
                        )))
                ),
                List.of(new HttpFeederPlan("users", "csv", "users.csv", "circular", List.of("username", "password"))),
                null,
                InjectionProfilePlan.defaultOpenModel(),
                List.of(AssertionPlan.defaultFailedRequests())
        );

        var feederRisk = service.checkFeederRisk(plan, "username,password\nu1,p1\n", 1, 60);
        assertThat(feederRisk.findings())
                .extracting(TroubleshootingFinding::code)
                .contains("feeder.undefined")
                .doesNotContain("feeder.placeholders_without_feeder");

        var correlationRisk = service.checkCorrelationRisk(plan, "");
        assertThat(correlationRisk.findings())
                .extracting(TroubleshootingFinding::code)
                .doesNotContain("correlation.reference_without_save", "correlation.auth_without_token_capture");
    }

    @Test
    void detectsWeakAssertionQuality() {
        var plan = new HttpSimulationPlan(
                "WeakAssertionsSimulation",
                "Weak assertions",
                "https://api.example.test",
                List.of(new HttpRequestPlan("GET /orders", "GET", "/orders", Map.of(), "", List.of())),
                null,
                null,
                null,
                InjectionProfilePlan.defaultOpenModel(),
                List.of()
        );

        var result = service.checkAssertionQuality(plan, "");

        assertThat(result.findings())
                .extracting(TroubleshootingFinding::code)
                .contains(
                        "assertion.missing_global_assertions",
                        "assertion.missing_error_budget",
                        "assertion.latency_budget_missing",
                        "assertion.request_status_checks_missing"
                );
        assertThat(result.nextActions()).anySatisfy(action ->
                assertThat(action).contains("status/error-rate/latency"));
    }

    private static HttpRequestPlan requestWithTokenReference() {
        return new HttpRequestPlan(
                "GET /api/orders",
                "GET",
                "/api/orders",
                Map.of("Authorization", "Bearer #{jwtToken}"),
                "",
                List.of(new CheckPlan("status", "", "is", "200", ""))
        );
    }
}
